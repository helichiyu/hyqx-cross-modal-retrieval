"""404m_embed 多模态向量化算子。

四通道：
- 文本/图像/视频帧：Chinese-CLIP（ViT-B/16），512 维，L2 归一
- 音频：CLAP（laion/clap-htsat-fused），512 维，L2 归一

两种用法：
1. 批处理：inputFile = 403 产出的 items.jsonl → embeddings.jsonl
2. 在线单条：payload 带 queryText / queryImagePath / queryAudioPath → 直接返回向量
   （供 408m_retrieval 在线链路复用同一模型实例）

模型权重从 config.paths.model_root 下加载，首次使用时懒加载并常驻。
"""

from __future__ import annotations

import json
import shutil
import sys
import threading
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from operators.common.base import OperatorBase, OperatorError  # noqa: E402

BATCH_SIZE = 16


class _EncoderHub:
    """懒加载的模型仓库，进程内单例。"""

    def __init__(self, model_root: Path, clip_name: str, clap_name: str) -> None:
        self.clip_dir = model_root / clip_name
        self.clap_dir = model_root / clap_name
        self._clip = None
        self._clap = None
        self._lock = threading.Lock()

    def clip(self):
        with self._lock:
            if self._clip is None:
                import torch
                from transformers import ChineseCLIPModel, ChineseCLIPProcessor
                model = ChineseCLIPModel.from_pretrained(str(self.clip_dir))
                processor = ChineseCLIPProcessor.from_pretrained(str(self.clip_dir))
                model.eval()
                self._clip = (model, processor, torch)
            return self._clip

    def clap(self):
        with self._lock:
            if self._clap is None:
                import torch
                from transformers import ClapModel, ClapProcessor
                model = ClapModel.from_pretrained(str(self.clap_dir))
                processor = ClapProcessor.from_pretrained(str(self.clap_dir))
                model.eval()
                self._clap = (model, processor, torch)
            return self._clap

    def loaded(self) -> dict:
        return {"chinese_clip": self._clip is not None, "clap": self._clap is not None}


def _l2_normalize(v: np.ndarray) -> np.ndarray:
    norm = np.linalg.norm(v, axis=-1, keepdims=True)
    return v / np.maximum(norm, 1e-12)


def _to_numpy(feats) -> np.ndarray:
    """兼容 transformers v5：get_*_features 返回 BaseModelOutputWithPooling。"""
    if hasattr(feats, "pooler_output"):
        feats = feats.pooler_output
    return feats.cpu().numpy().astype(np.float32)


def _encode_texts(hub: _EncoderHub, texts: list[str]) -> np.ndarray:
    model, processor, torch = hub.clip()
    inputs = processor(text=texts, return_tensors="pt", padding=True, truncation=True)
    with torch.no_grad():
        feats = model.get_text_features(**inputs)
    return _l2_normalize(_to_numpy(feats))


def _encode_images(hub: _EncoderHub, paths: list[str]) -> np.ndarray:
    from PIL import Image
    model, processor, torch = hub.clip()
    images = [Image.open(p).convert("RGB") for p in paths]
    inputs = processor(images=images, return_tensors="pt")
    with torch.no_grad():
        feats = model.get_image_features(**inputs)
    return _l2_normalize(_to_numpy(feats))


def _encode_audios(hub: _EncoderHub, paths: list[str]) -> np.ndarray:
    import soundfile as sf
    model, processor, torch = hub.clap()
    waves = []
    for p in paths:
        data, sr = sf.read(p, dtype="float32")
        if data.ndim > 1:
            data = data.mean(axis=1)
        waves.append(data)
    inputs = processor(audio=waves, sampling_rate=48000, return_tensors="pt", padding=True)
    with torch.no_grad():
        feats = model.get_audio_features(**inputs)
    return _l2_normalize(_to_numpy(feats))


def _encode_texts_clap(hub: _EncoderHub, texts: list[str]) -> np.ndarray:
    """CLAP 文本塔：文本查询检索音频通道时用（CLAP 是文本-音频对齐空间）。"""
    model, processor, torch = hub.clap()
    inputs = processor(text=texts, return_tensors="pt", padding=True, truncation=True)
    with torch.no_grad():
        feats = model.get_text_features(**inputs)
    return _l2_normalize(_to_numpy(feats))


def _batched(items: list, size: int):
    for i in range(0, len(items), size):
        yield items[i:i + size]


class Operator(OperatorBase):
    """多模态向量化：items.jsonl → embeddings.jsonl，或单条查询 → 向量。"""

    def __init__(self, operator_id: str, cfg: dict) -> None:
        super().__init__(operator_id, cfg)
        m = cfg["models"]
        self.hub = _EncoderHub(Path(cfg["paths"]["model_root"]), m["chinese_clip"], m["clap"])
        self.model_version = m["model_version"]

    def health(self) -> dict:
        h = super().health()
        h["models"] = self.hub.loaded()
        h["modelVersion"] = self.model_version
        # 权重不完整（缺 config.json）视为不可用，便于部署自检
        h["weightsPresent"] = {
            "chinese_clip": (self.hub.clip_dir / "config.json").exists(),
            "clap": (self.hub.clap_dir / "config.json").exists(),
        }
        if not all(h["weightsPresent"].values()):
            h["status"] = "degraded"
        return h

    def predict_payload(
        self, payload: dict, unique_id: str, input_file: str | None, task_out: Path
    ) -> dict:
        # 在线单条查询
        if payload.get("queryText") or payload.get("queryImagePath") or payload.get("queryAudioPath"):
            return self._embed_query(payload)

        if input_file is None:
            raise OperatorError("需要 inputFile(items.jsonl) 或 queryText/queryImagePath/queryAudioPath")
        items_path = Path(input_file)
        if items_path.suffix != ".jsonl":
            raise OperatorError("404 的 inputFile 必须是 items.jsonl")

        items = [json.loads(line) for line in items_path.read_text(encoding="utf-8").splitlines() if line.strip()]
        if not items:
            raise OperatorError("items.jsonl is empty")

        out_path = task_out / "embeddings.jsonl"
        count = 0
        try:
            with open(out_path, "w", encoding="utf-8") as f:
                for batch in _batched(items, BATCH_SIZE):
                    vectors = self._embed_batch(batch)
                    for it, vec in zip(batch, vectors):
                        f.write(json.dumps({
                            "itemId": it["itemId"],
                            "modality": it["modality"],
                            "vector": [round(float(x), 6) for x in vec],
                        }, ensure_ascii=False) + "\n")
                        count += 1
        finally:
            # 回源模式清理：成功或失败都删除 403 临时下载的上游原始内容与抽帧/切片产物。
            # 任务重试会从 401 重跑整链并重新下载，因此失败清理不会破坏重试。
            if payload.get("cleanupSources", True):
                self._cleanup_temp(items_path)

        return {
            "outputFile": str(out_path),
            "itemCount": count,
            "dim": 512,
            "modelVersion": self.model_version,
        }

    @staticmethod
    def _cleanup_temp(items_path: Path) -> None:
        base = items_path.parent
        for name in ("_source", "_derived"):
            temp = base / name
            if temp.is_dir():
                shutil.rmtree(temp, ignore_errors=True)

    # ---- 内部 ----

    def _embed_query(self, payload: dict) -> dict:
        if payload.get("queryText"):
            # channel=clap：用 CLAP 文本塔，供文本查询检索音频通道
            if payload.get("channel") == "clap":
                vec = _encode_texts_clap(self.hub, [payload["queryText"]])[0]
                modality = "text"
            else:
                modality, vec = "text", _encode_texts(self.hub, [payload["queryText"]])[0]
        elif payload.get("queryImagePath"):
            p = self.resolve_input(payload["queryImagePath"])
            modality, vec = "image", _encode_images(self.hub, [str(p)])[0]
        else:
            p = self.resolve_input(payload["queryAudioPath"])
            modality, vec = "audio", _encode_audios(self.hub, [str(p)])[0]
        return {
            "modality": modality,
            "dim": int(vec.shape[0]),
            "vector": [round(float(x), 6) for x in vec],
            "modelVersion": self.model_version,
        }

    def _embed_batch(self, batch: list[dict]) -> np.ndarray:
        modality = batch[0]["modality"]
        same = [b for b in batch if b["modality"] == modality]
        rest = [b for b in batch if b["modality"] != modality]
        vecs: dict[int, np.ndarray] = {}
        if same:
            if modality == "text":
                v = _encode_texts(self.hub, [b["text"] for b in same])
            elif modality in ("image", "video"):
                v = _encode_images(self.hub, [str(self.resolve_input(b["locator"]["path"])) for b in same])
            elif modality == "audio":
                v = _encode_audios(self.hub, [str(self.resolve_input(b["locator"]["path"])) for b in same])
            else:
                raise OperatorError(f"unknown modality: {modality}")
            for b, x in zip(same, v):
                vecs[id(b)] = x
        if rest:
            for b, x in zip(rest, self._embed_batch(rest)):
                vecs[id(b)] = x
        return np.stack([vecs[id(b)] for b in batch])
