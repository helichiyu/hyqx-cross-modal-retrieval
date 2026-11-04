"""401m_ingest_check 数据接入校验算子。

对上传/指定的数据集目录做三项校验（对应数据流 3.1"接入"）：
1. 模态识别：魔数 + 扩展名双重判定，不一致记疑似
2. 可解码性：逐文件试解码（图片 PIL verify / 音视频 ffmpeg 探测 / 文本 utf-8 试读）
3. 条数与场景核对：若目录内有 metadata.json（name/count/modalities/scenes），核对实际与声明

产出 report.json（含 decodableRate/modalityCoverage/countRate，供 402 评定直接取用）
+ 失败样本清单 + lineage.jsonl。不做通过/挂起/剔除判定（那是 402 的职责）。
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
