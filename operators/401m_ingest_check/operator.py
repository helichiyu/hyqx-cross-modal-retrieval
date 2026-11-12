"""401m_ingest_check 数据接入校验算子。

两种输入模式（对应数据流 3.1"接入"）：

1. manifest 模式（数据源回源接入，平台只保留向量与元数据）：目录内存在
   manifest.jsonl 时按行校验条目契约——itemId、1-4 个 modals、文本 content
   非空、媒体为 http(s) URL。不下载任何原始内容。
2. 目录模式（本地目录/压缩包接入）：逐文件做模态识别（魔数 + 扩展名）、
   可解码性探测（PIL / ffmpeg / utf-8 试读）。

两种模式都会在目录内有 metadata.json 时核对条数与模态声明，产出
report.json + 失败样本清单 + lineage.jsonl。不做通过/挂起/剔除判定。
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from operators.common.base import OperatorBase, OperatorError  # noqa: E402

EXT_MODALITY = {
    ".txt": "text", ".md": "text", ".json": "text", ".csv": "text",
    ".jpg": "image", ".jpeg": "image", ".png": "image", ".bmp": "image",
    ".webp": "image", ".gif": "image",
    ".wav": "audio", ".mp3": "audio", ".flac": "audio", ".m4a": "audio", ".ogg": "audio",
    ".mp4": "video", ".mov": "video", ".avi": "video", ".mkv": "video", ".webm": "video",
}

MODALITIES = ("text", "image", "audio", "video")


def _ffmpeg() -> str:
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def _magic_modality(path: Path) -> str | None:
    try:
        with open(path, "rb") as f:
            head = f.read(16)
    except OSError:
        return None
    if head[:3] == b"\xff\xd8\xff" or head[:8] == b"\x89PNG\r\n\x1a\n" or head[:4] == b"GIF8" \
            or head[:2] == b"BM" or (head[:4] == b"RIFF" and head[8:12] == b"WEBP"):
        return "image"
    if (head[:4] == b"RIFF" and head[8:12] == b"WAVE") or head[:3] == b"ID3" \
            or head[:2] in (b"\xff\xfb", b"\xff\xf3", b"\xff\xf2") or head[:4] == b"fLaC" \
            or head[:4] == b"OggS":
        return "audio"
    if head[4:8] == b"ftyp":
        return "video"
    return None  # 文本无魔数，靠扩展名 + 试读


def _is_http_url(value: object) -> bool:
    return isinstance(value, str) and value.startswith(("http://", "https://"))


def _read_manifest(path: Path) -> list[dict]:
    lines = []
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip():
            continue
        try:
            lines.append(json.loads(raw))
        except json.JSONDecodeError as e:
            raise OperatorError(f"manifest.jsonl 第 {lineno} 行不是合法 JSON: {e}")
    return lines


def _decodable(path: Path, modality: str) -> str | None:
    """可解码返回 None，否则返回失败原因。"""
    try:
        if modality == "text":
            path.read_text(encoding="utf-8")
            return None
        if modality == "image":
            from PIL import Image
            with Image.open(path) as im:
                im.verify()
            return None
        # 音视频用 ffmpeg 探测
        proc = subprocess.run(
            [_ffmpeg(), "-v", "error", "-i", str(path), "-f", "null", "-"],
            capture_output=True, text=True, timeout=120,
        )
        if proc.returncode != 0 or proc.stderr.strip():
            return (proc.stderr.strip() or "decode error")[:200]
        return None
    except Exception as e:
        return str(e)[:200]


class Operator(OperatorBase):
    """接入校验：数据集目录 → report.json + lineage.jsonl。"""

    def predict_payload(
        self, payload: dict, unique_id: str, input_file: str | None, task_out: Path
    ) -> dict:
        if input_file is not None:
            src_dir = Path(input_file)
            if not src_dir.is_dir():
                raise OperatorError("401 的 inputFile 必须是数据集目录")
        else:
            src_dir = self.input_root / unique_id
        if (src_dir / "manifest.jsonl").exists():
            return self._check_manifest(src_dir, task_out, unique_id)
        files = [
            p for p in sorted(src_dir.rglob("*"))
            if p.is_file() and p.suffix.lower() != ".zip" and p.name != "metadata.json"
        ]
        if not files:
            raise OperatorError(f"no input files under {src_dir}")

        detected: dict[str, int] = {m: 0 for m in MODALITIES}
        decodable: dict[str, int] = {m: 0 for m in MODALITIES}
        failures: list[dict] = []
        mismatched: list[dict] = []

        lineage_path = task_out / "lineage.jsonl"
        with open(lineage_path, "w", encoding="utf-8") as fl:
            for f in files:
                ext_mod = EXT_MODALITY.get(f.suffix.lower())
                magic_mod = _magic_modality(f)
                modality = magic_mod or ext_mod
                if modality is None:
                    failures.append({"file": self.relpath(f), "reason": "unsupported type"})
                    fl.write(json.dumps({"uniqueId": unique_id, "operatorId": self.operator_id,
                                         "sourceFile": self.relpath(f), "filtered": True,
                                         "reason": "unsupported type"}, ensure_ascii=False) + "\n")
                    continue
                if ext_mod and magic_mod and ext_mod != magic_mod:
                    mismatched.append({"file": self.relpath(f), "extSays": ext_mod, "magicSays": magic_mod})
                detected[modality] += 1
                err = _decodable(f, modality)
                if err is None:
                    decodable[modality] += 1
                    fl.write(json.dumps({"uniqueId": unique_id, "operatorId": self.operator_id,
                                         "sourceFile": self.relpath(f), "modality": modality,
                                         "decodable": True}, ensure_ascii=False) + "\n")
                else:
                    failures.append({"file": self.relpath(f), "reason": err})
                    fl.write(json.dumps({"uniqueId": unique_id, "operatorId": self.operator_id,
                                         "sourceFile": self.relpath(f), "modality": modality,
                                         "filtered": True, "reason": err}, ensure_ascii=False) + "\n")

        # 元数据核对（有 metadata.json 才做）
        reconcile = None
        meta_file = src_dir / "metadata.json"
        if meta_file.exists():
            declared = json.loads(meta_file.read_text(encoding="utf-8"))
            declared_mods = set(declared.get("modalities", []))
            detected_mods = {m for m, n in detected.items() if n > 0}
            declared_count = declared.get("count")
            reconcile = {
                "declaredCount": declared_count,
                "actualCount": len(files),
                "countMatch": declared_count is None or declared_count == len(files),
                "declaredModalities": sorted(declared_mods),
                "detectedModalities": sorted(detected_mods),
                "modalityMatch": not declared_mods or declared_mods == detected_mods,
                "scenes": declared.get("scenes", []),
            }

        total = len(files)
        total_decodable = sum(decodable.values())
        declared_mods = set(reconcile["declaredModalities"]) if reconcile else set()
        expected_mods = declared_mods or set(MODALITIES)
        report = {
            "datasetDir": self.relpath(src_dir),
            "totalFiles": total,
            "detected": detected,
            "decodable": decodable,
            "decodableRate": round(total_decodable / total, 4),
            "modalityCoverage": round(len({m for m, n in detected.items() if n > 0} & expected_mods) / len(expected_mods), 4),
            "countRate": (round(min(total / reconcile["declaredCount"], 1.0), 4)
                          if reconcile and reconcile.get("declaredCount") else 1.0),
            "mismatchedExt": mismatched,
            "reconcile": reconcile,
            "failureCount": len(failures),
        }
        report_path = task_out / "report.json"
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        (task_out / "failures.json").write_text(json.dumps(failures, ensure_ascii=False, indent=2), encoding="utf-8")

        return {
            "outputFile": str(report_path),
            "lineageFile": str(lineage_path),
            "failuresFile": str(task_out / "failures.json"),
            **{k: report[k] for k in ("totalFiles", "decodableRate", "modalityCoverage", "countRate", "failureCount")},
        }

    def _check_manifest(self, src_dir: Path, task_out: Path, unique_id: str) -> dict:
        """manifest 模式：只校验条目契约，不下载原始内容、不做解码探测。"""
        entries = _read_manifest(src_dir / "manifest.jsonl")
        if not entries:
            raise OperatorError(f"manifest.jsonl 为空: {src_dir}")

        detected: dict[str, int] = {m: 0 for m in MODALITIES}
        failures: list[dict] = []
        seen_ids: set[str] = set()
        lineage_path = task_out / "lineage.jsonl"

        with open(lineage_path, "w", encoding="utf-8") as fl:
            for idx, entry in enumerate(entries):
                item_id = entry.get("itemId") if isinstance(entry, dict) else None
                if not isinstance(item_id, str) or not item_id.strip():
                    failures.append({"itemIndex": idx, "reason": "缺少 itemId"})
                    continue
                if item_id in seen_ids:
                    failures.append({"itemId": item_id, "reason": "itemId 重复"})
                    continue
                seen_ids.add(item_id)
                modals = entry.get("modals")
                if not isinstance(modals, list) or not 1 <= len(modals) <= 4:
                    failures.append({"itemId": item_id, "reason": "modals 必须为 1-4 项"})
                    continue
                mods: list[str] = []
                invalid = None
                for modal in modals:
                    if not isinstance(modal, dict):
                        invalid = "modal 不是对象"
                        break
                    mtype = modal.get("type")
                    if mtype not in MODALITIES:
                        invalid = f"未知模态 {mtype}"
                        break
                    if mtype == "text":
                        content = modal.get("content")
                        if not isinstance(content, str) or not content.strip():
                            invalid = "文本条目 content 为空"
                            break
                    elif not _is_http_url(modal.get("url")):
                        invalid = f"{mtype} 条目缺少 http(s) url"
                        break
                    mods.append(mtype)
                if invalid:
                    failures.append({"itemId": item_id, "reason": invalid})
                    continue
                for m in mods:
                    detected[m] += 1
                fl.write(json.dumps({"uniqueId": unique_id, "operatorId": self.operator_id,
                                     "sourceFile": item_id, "modalities": mods,
                                     "decodable": True}, ensure_ascii=False) + "\n")

        reconcile = None
        meta_file = src_dir / "metadata.json"
        if meta_file.exists():
            declared = json.loads(meta_file.read_text(encoding="utf-8"))
            declared_mods = set(declared.get("modalities", []))
            detected_mods = {m for m, n in detected.items() if n > 0}
            declared_count = declared.get("count")
            reconcile = {
                "declaredCount": declared_count,
                "actualCount": len(entries),
                "countMatch": declared_count is None or declared_count == len(entries),
                "declaredModalities": sorted(declared_mods),
                "detectedModalities": sorted(detected_mods),
                "modalityMatch": not declared_mods or declared_mods == detected_mods,
                "scenes": declared.get("scenes", []),
            }

        total = len(entries)
        failed = len(failures)
        expected_mods = set(reconcile["declaredModalities"]) if reconcile else set()
        expected_mods = expected_mods or set(MODALITIES)
        report = {
            "mode": "manifest",
            "datasetDir": self.relpath(src_dir),
            "totalFiles": total,
            "totalItems": total,
            "detected": detected,
            "decodable": detected,
            "decodableRate": round((total - failed) / total, 4),
            "modalityCoverage": round(len({m for m, n in detected.items() if n > 0} & expected_mods) / len(expected_mods), 4),
            "countRate": (round(min(total / reconcile["declaredCount"], 1.0), 4)
                          if reconcile and reconcile.get("declaredCount") else 1.0),
            "mismatchedExt": [],
            "reconcile": reconcile,
            "failureCount": failed,
        }
        report_path = task_out / "report.json"
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        (task_out / "failures.json").write_text(json.dumps(failures, ensure_ascii=False, indent=2), encoding="utf-8")
        return {
            "outputFile": str(report_path),
            "lineageFile": str(lineage_path),
            "failuresFile": str(task_out / "failures.json"),
            "mode": "manifest",
            **{k: report[k] for k in ("totalFiles", "decodableRate", "modalityCoverage", "countRate", "failureCount")},
        }
