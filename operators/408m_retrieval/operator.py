"""408m_retrieval 检索服务算子（在线常驻）。

对应数据流 3.2/技术流：查询向量化 → ANN 召回 → 多模态融合（策略权重加权）→ 可选精排 → 结果集 RST。

对外主接口：POST /408m_retrieval/search（同步、低延迟，前后端直连）。
四协议的 /predict 复用同一检索逻辑，保持管线兼容。

融合算法（借鉴 DigUp，其实测优于 RRF）：
- 每通道余弦分做 z-score 归一，取 max(z, 0)
- 按策略四模态权重加权（合计必须 100）
- 精确词命中加成：wordWeight * coverage^1.5（发票号/错误码这类 embedding 保不住的词）

通道规则：
- 文本查询 → CLIP 文本向量检 clip 通道（文/图/视频帧）+ CLAP 文本向量检 clap 通道（音频）
- 图片/视频查询 → clip 通道；音频查询 → clap 通道
"""

from __future__ import annotations
import json
import math
import sys
import threading
import time
import uuid
from pathlib import Path

import numpy as np
from fastapi import APIRouter, Request
from starlette.concurrency import run_in_threadpool

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from operators.common import index_store  # noqa: E402
from operators.common.base import OperatorBase, OperatorError  # noqa: E402

MODALITIES = ("text", "image", "audio", "video")


class _Reranker:
    """bge-reranker-v2-m3 懒加载单例。"""

    def __init__(self, model_dir: Path) -> None:
        self.model_dir = model_dir
        self._model = None
        self._lock = threading.Lock()

    def score(self, pairs: list[tuple[str, str]]) -> list[float]:
        with self._lock:
            if self._model is None:
                import torch
                from transformers import AutoModelForSequenceClassification, AutoTokenizer
                tok = AutoTokenizer.from_pretrained(str(self.model_dir))
                model = AutoModelForSequenceClassification.from_pretrained(str(self.model_dir))
                model.eval()
                self._model = (model, tok, torch)
        model, tok, torch = self._model
        inputs = tok(pairs, padding=True, truncation=True, return_tensors="pt", max_length=512)
        with torch.no_grad():
            logits = model(**inputs).logits.view(-1).float()
        return torch.sigmoid(logits).tolist()

    @property
    def loaded(self) -> bool:
        return self._model is not None


class Operator(OperatorBase):
    """检索服务：召回 + 融合 + 重排 → 结果集 RST。"""

    def __init__(self, operator_id: str, cfg: dict) -> None:
        super().__init__(operator_id, cfg)
        self.rt = cfg.get("retrieval", {})
        self.model_version = cfg["models"]["model_version"]
        self.reranker = _Reranker(Path(cfg["paths"]["model_root"]) / cfg["models"]["reranker"])

    def extra_router(self):
        router = APIRouter()

        @router.post("/search")
        async def search(request: Request):
            try:
                payload = await request.json()
            except Exception:
                return {"code": 1, "msg": "invalid json body", "data": {}}
            try:
                result = await run_in_threadpool(self.search, payload)
            except OperatorError as e:
                return {"code": 1, "msg": str(e), "data": {}}
            return {"code": 0, "msg": "success", "data": result}

        return router

    def health(self) -> dict:
        h = super().health()
        h["rerankerLoaded"] = self.reranker.loaded
        h["activeIndex"] = self.rt.get("active_index")
        return h

    # 四协议入口：与 /search 同逻辑
    def predict_payload(self, payload, unique_id, input_file, task_out) -> dict:
        if input_file and not payload.get("queryFile"):
            payload = dict(payload, queryFile=input_file)
        result = self.search(payload)
        out = task_out / "result_set.json"
        out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        return {**result, "outputFile": str(out)}

    # ---- 检索主逻辑 ----

    def search(self, payload: dict) -> dict:
        query_text = (payload.get("query") or "").strip()
        query_file = payload.get("queryFile")
        if not query_text and not query_file:
            raise OperatorError("需要 query 文本或 queryFile")

        index_dir = self._resolve_index_dir(payload.get("indexDir"))
        index_store.check_model_version(index_dir, self.model_version)

        targets = payload.get("targetModalities") or list(MODALITIES)
        weights = payload.get("weights") or self.rt.get("weights") or {}
        self._check_weights(weights, targets)
        top_k = int(payload.get("topK", self.rt.get("top_k", 20)))
        rerank = bool(payload.get("rerank", self.rt.get("rerank", False)))
        word_weight = float(payload.get("wordWeight", self.rt.get("word_weight", 2.0)))
        threshold = payload.get("threshold")
        threshold = None if threshold is None else float(threshold)

        unique_id = payload.get("uniqueId") or f"anon_{uuid.uuid4().hex[:8]}"
        started = time.time()

        # 1. 召回：按查询模态确定要搜的通道
        candidates: dict[str, dict] = {}  # itemId -> {meta, z}
        if query_text:
            if set(targets) & {"text", "image", "video"}:
                self._collect(index_dir, self._embed(unique_id, queryText=query_text),
                              "clip", top_k, candidates)
            if "audio" in targets:
                self._collect(index_dir, self._embed(unique_id, queryText=query_text, channel="clap"),
                              "clap", top_k, candidates)
            query_modality = "text"
        else:
            p = str(self.resolve_input(str(query_file)))
            ext = Path(p).suffix.lower()
            if ext in (".jpg", ".jpeg", ".png", ".bmp", ".webp", ".gif", ".mp4", ".mov", ".avi", ".mkv", ".webm"):
                self._collect(index_dir, self._embed(unique_id, queryImagePath=p), "clip", top_k, candidates)
                query_modality = "video" if ext in (".mp4", ".mov", ".avi", ".mkv", ".webm") else "image"
            elif ext in (".wav", ".mp3", ".flac", ".m4a", ".ogg"):
                self._collect(index_dir, self._embed(unique_id, queryAudioPath=p), "clap", top_k, candidates)
                query_modality = "audio"
            else:
                raise OperatorError(f"无法识别查询文件模态: {Path(p).name}")

        if not candidates:
            return self._result(payload, query_text, query_modality, [], weights, rerank, threshold, started)

        # 2. 融合：z-score 加权 + 精确词加成
        fused = self._fuse(list(candidates.values()), weights, word_weight, query_text)

        # 2.1 相似度阈值：按原始余弦（alignScore）过滤，低于策略阈值的不下发
        if threshold is not None:
            fused = [it for it in fused if it.get("alignScore", 0.0) >= threshold]

        # 3. 可选精排：bge-reranker 只对含文本的条目打分（reranker 是文本模型）
        if rerank and fused:
            fused = self._rerank(fused[: int(self.rt.get("rerank_top_n", 50))], query_text) + fused[int(self.rt.get("rerank_top_n", 50)):]
        fused.sort(key=lambda x: x["score"], reverse=True)
        items = fused[:top_k]
        for i, it in enumerate(items):
            it["rank"] = i + 1
        return self._result(payload, query_text, query_modality, items, weights, rerank, threshold, started)

    # ---- 内部 ----

    def _resolve_index_dir(self, index_dir: str | None) -> Path:
        d = Path(index_dir or self.rt.get("active_index", ""))
        if not d.is_absolute():
            d = Path(self.cfg["paths"]["project_root"]) / d
        if not d.resolve().exists():
            raise OperatorError(f"索引不存在: {d}（请先完成建库链）")
        return d.resolve()

    def _check_weights(self, weights: dict, targets: list[str]) -> None:
        total = sum(float(weights.get(m, 0)) for m in MODALITIES)
        if abs(total - 100.0) > 0.01:
            raise OperatorError(f"融合权重合计必须为 100，当前为 {total:g}")

    def _embed(self, unique_id: str, **kwargs) -> np.ndarray:
        import httpx
        port = self.cfg["server"]["port"]
        r = httpx.post(f"http://127.0.0.1:{port}/404m_embed/predict",
                       json={"uniqueId": unique_id, **kwargs}, timeout=300)
        body = r.json()
        if body.get("code") != 0:
            raise OperatorError(f"404 向量化失败: {body.get('msg')}")
        return np.array(body["data"]["vector"], dtype=np.float32)

    def _collect(self, index_dir: Path, vec: np.ndarray, channel: str,
                 top_k: int, candidates: dict[str, dict]) -> None:
        top = index_store.search(index_dir, vec, channel, top_k * 5)
        metas = index_store.get_items(index_dir, [rid for rid, _ in top])
        scores = np.array([s for _, s in top], dtype=np.float32)
        mean, std = float(scores.mean()), max(float(scores.std()), 1e-6)
        for rid, s in top:
            meta = metas.get(rid)
            if not meta:
                continue
            z = max((s - mean) / std, 0.0)
            item_id = meta["itemId"]
            if item_id not in candidates or z > candidates[item_id]["z"]:
                # sim 保留原始余弦（0~1），供 similarity 阈值过滤与对齐分展示
                candidates[item_id] = {"meta": meta, "z": z, "sim": float(s)}

    def _fuse(self, cands: list[dict], weights: dict, word_weight: float,
              query_text: str) -> list[dict]:
        tokens = query_text.split() if " " in query_text else ([query_text] if query_text else [])
        out = []
        for c in cands:
            meta, z = c["meta"], c["z"]
            w = float(weights.get(meta["modality"], 0)) / 100.0
            score = w * z
            if tokens and meta.get("text"):
                coverage = sum(1 for t in tokens if t in meta["text"]) / len(tokens)
                score += word_weight * (coverage ** 1.5)
            out.append({
                "score": round(score, 6),
                **{k: meta[k] for k in ("itemId", "modality", "sourceFile", "locator", "text")},
                "source": _source_of(meta.get("sourceFile")),
                "alignScore": round(c.get("sim", 0.0), 6),
            })
        return out

    def _rerank(self, cands: list[dict], query_text: str) -> list[dict]:
        if not query_text or not cands:
            return cands
        text_idx = [i for i, c in enumerate(cands) if c.get("text")]
        if not text_idx:
            return cands
        pairs = [(query_text, cands[i]["text"]) for i in text_idx]
        scores = self.reranker.score(pairs)
        # 非文本条目保持融合分，映射到 [0,1] 与 sigmoid 后的重排分同尺度合并
        rest = [c["score"] for i, c in enumerate(cands) if i not in text_idx]
        lo, hi = (min(rest), max(rest)) if rest else (0.0, 1.0)
        span = max(hi - lo, 1e-6)
        for i, c in enumerate(cands):
            c["score"] = scores[text_idx.index(i)] if i in text_idx else (c["score"] - lo) / span
        return cands

    def _result(self, payload, query_text, query_modality, items, weights, rerank,
                threshold, started) -> dict:
        return {
            "resultSetId": "RST" + time.strftime("%Y%m%d%H%M%S") + uuid.uuid4().hex[:6].upper(),
            "query": query_text or None,
            "queryFile": payload.get("queryFile"),
            "queryModality": query_modality,
            "count": len(items),
            "items": items,
            "strategy": {"weights": weights, "rerank": rerank,
                         "topK": payload.get("topK", self.rt.get("top_k", 20)),
                         "threshold": threshold},
            "modelVersion": self.model_version,
            "elapsedMs": round((time.time() - started) * 1000, 1),
        }


def _source_of(source_file: str | None) -> str | None:
    """从源文件路径推导来源名（父目录名），与前端展示口径一致。"""
    if not source_file:
        return None
    parts = [p for p in str(source_file).replace("\\", "/").split("/") if p]
    return parts[-2] if len(parts) > 1 else None
