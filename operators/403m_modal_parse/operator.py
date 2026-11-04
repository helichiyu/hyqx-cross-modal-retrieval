"""403m_modal_parse 模态内容解析算子。

把上传的原始文件解析成标准化条目（items.jsonl）：
- 文本：按段落切分（约 500 字一段，50 字重叠）
- 图片：一图一条
- 音频：ffmpeg 切 30 秒一片，转 48kHz 单声道 wav（对齐 CLAP 输入）
- 视频：ffmpeg 按 1fps 抽帧出 jpg（音轨抽取待后续版本）

输入：uploadFile 上传的文件（zip 自动解压），或 inputFile 指向包含文件的目录。
输出：task_out/items.jsonl + lineage.jsonl。
"""

from __future__ import annotations

import json
import subprocess
import sys
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from operators.common.base import OperatorBase, OperatorError  # noqa: E402

TEXT_EXT = {".txt", ".md", ".json", ".csv"}
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".gif"}
AUDIO_EXT = {".wav", ".mp3", ".flac", ".m4a", ".ogg"}
VIDEO_EXT = {".mp4", ".mov", ".avi", ".mkv", ".webm"}

SEG_CHARS = 500
SEG_OVERLAP = 50
AUDIO_SLICE_SEC = 30
VIDEO_FPS = 1


def _ffmpeg() -> str:
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def _detect_modality(path: Path) -> str | None:
    ext = path.suffix.lower()
    if ext in TEXT_EXT:
        return "text"
    if ext in IMAGE_EXT:
        return "image"
    if ext in AUDIO_EXT:
        return "audio"
    if ext in VIDEO_EXT:
        return "video"
    return None


def _parse_text(path: Path) -> list[dict]:
    content = path.read_text(encoding="utf-8", errors="replace")
    items = []
    start, idx = 0, 0
    while start < len(content):
        seg = content[start:start + SEG_CHARS].strip()
        if seg:
            items.append({
                "modality": "text",
                "text": seg,
                "locator": {"segment": idx, "charStart": start},
            })
            idx += 1
        start += SEG_CHARS - SEG_OVERLAP
    return items


def _parse_image(path: Path) -> list[dict]:
    return [{"modality": "image", "locator": {"path": str(path)}}]


def _run_ffmpeg(args: list[str]) -> None:
    proc = subprocess.run(
        [_ffmpeg(), "-y", "-hide_banner", "-loglevel", "error", *args],
        capture_output=True, text=True,
    )
    if proc.returncode != 0:
        raise OperatorError(f"ffmpeg failed: {proc.stderr.strip()[:300]}")


def _parse_audio(path: Path, out_dir: Path) -> list[dict]:
    slice_dir = out_dir / f"{path.stem}_slices"
    slice_dir.mkdir(parents=True, exist_ok=True)
    pattern = str(slice_dir / "slice_%04d.wav")
    _run_ffmpeg([
        "-i", str(path),
        "-f", "segment", "-segment_time", str(AUDIO_SLICE_SEC),
        "-ac", "1", "-ar", "48000", "-c:a", "pcm_s16le",
        pattern,
    ])
    items = []
    for i, wav in enumerate(sorted(slice_dir.glob("slice_*.wav"))):
        items.append({
            "modality": "audio",
            "locator": {
                "path": str(wav),
                "startSec": i * AUDIO_SLICE_SEC,
                "endSec": (i + 1) * AUDIO_SLICE_SEC,
            },
        })
    return items


def _parse_video(path: Path, out_dir: Path) -> list[dict]:
    frame_dir = out_dir / f"{path.stem}_frames"
    frame_dir.mkdir(parents=True, exist_ok=True)
    pattern = str(frame_dir / "frame_%05d.jpg")
    _run_ffmpeg(["-i", str(path), "-vf", f"fps={VIDEO_FPS}", "-q:v", "3", pattern])
    items = []
    for i, frame in enumerate(sorted(frame_dir.glob("frame_*.jpg"))):
        items.append({
            "modality": "video",
            "locator": {"path": str(frame), "timeSec": round(i / VIDEO_FPS, 3)},
        })
    return items


class Operator(OperatorBase):
    """模态内容解析：目录/上传文件 → items.jsonl。"""

    def predict_payload(
        self, payload: dict, unique_id: str, input_file: str | None, task_out: Path
    ) -> dict:
        # 解析对象：inputFile 指定的目录，或本任务上传目录
        if input_file is not None:
            src = Path(input_file)
            if not src.is_dir():
                raise OperatorError("403 的 inputFile 必须是包含原始文件的目录")
            src_dir = src
        else:
            src_dir = self.input_root / unique_id
        files = [
            p for p in sorted(src_dir.rglob("*"))
            if p.is_file() and p.suffix.lower() != ".zip" and p.name != "metadata.json"
        ]
        if not files:
            raise OperatorError(f"no input files under {src_dir}")

        items_path = task_out / "items.jsonl"
        lineage_path = task_out / "lineage.jsonl"
        count = {"text": 0, "image": 0, "audio": 0, "video": 0}
        skipped = []

        with open(items_path, "w", encoding="utf-8") as fi, \
                open(lineage_path, "w", encoding="utf-8") as fl:
            for f in files:
                modality = _detect_modality(f)
                if modality is None:
                    skipped.append({"file": self.relpath(f), "reason": "unsupported type"})
                    continue
                if modality == "text":
                    items = _parse_text(f)
                elif modality == "image":
                    items = _parse_image(f)
                elif modality == "audio":
                    items = _parse_audio(f, task_out)
                else:
                    items = _parse_video(f, task_out)
                count[modality] += len(items)
                for it in items:
                    it["itemId"] = uuid.uuid4().hex[:16]
                    it["sourceFile"] = self.relpath(f)
                    # 音频切片/视频帧产物同样输出逻辑路径，供后端直接挂载还原
                    if "path" in it.get("locator", {}):
                        it["locator"]["path"] = self.relpath(it["locator"]["path"])
                    fi.write(json.dumps(it, ensure_ascii=False) + "\n")
                fl.write(json.dumps({
                    "uniqueId": unique_id,
                    "operatorId": self.operator_id,
                    "sourceFile": self.relpath(f),
                    "modality": modality,
                    "itemCount": len(items),
                }, ensure_ascii=False) + "\n")
        for s in skipped:
            with open(lineage_path, "a", encoding="utf-8") as fl:
                fl.write(json.dumps({
                    "uniqueId": unique_id, "operatorId": self.operator_id,
                    "sourceFile": s["file"], "filtered": True, "reason": s["reason"],
                }, ensure_ascii=False) + "\n")

        return {
            "outputFile": str(items_path),
            "lineageFile": str(lineage_path),
            "itemCount": sum(count.values()),
            "byModality": count,
            "skipped": skipped,
        }
