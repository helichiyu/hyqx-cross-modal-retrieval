"""403m_modal_parse 模态内容解析算子。

把原始内容解析成标准化条目（items.jsonl）：

1. manifest 模式（数据源回源接入）：目录内存在 manifest.jsonl 时，按条目
   的 modals 逐项处理——文本直接取 content；图片/音频/视频按 URL 流式下载
   到临时目录后解析，sourceFile 记为上游 URL。音频切 30 秒 wav、视频按
   1fps 抽帧；解析完立即删除下载的原始文件，派生产物由 404 向量化后清理。
2. 目录模式（本地目录/压缩包接入）：文本按段落切分（约 500 字一段，50 字
   重叠）、图片一图一条、音频切片、视频抽帧，sourceFile 为文件逻辑路径。

输出：task_out/items.jsonl + lineage.jsonl。
"""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
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


def _stable_id(item: dict) -> str:
    """条目 ID = 模态 + 源文件 + 文件内位置的稳定哈希。

    不用随机 uuid：重建会重跑整链，itemId 一变，历史检索结果、评测标注
    与自动合成的默认评测集（query→itemId 自评）全部失配，406 必然判失败。
    位置取切片/抽帧产物文件名（slice_0000.wav、frame_0001.jpg）或文本段序号，
    与任务号无关，故重复解析结果一致。
    """
    locator = item.get("locator") or {}
    if locator.get("path"):
        position = Path(locator["path"]).name
    elif "segment" in locator:
        position = f"segment-{locator['segment']}"
    else:
        position = json.dumps(locator, sort_keys=True, ensure_ascii=False)
    key = f"{item.get('modality')}|{item.get('sourceFile')}|{position}"
    return hashlib.sha1(key.encode("utf-8")).hexdigest()[:16]


def _ffmpeg() -> str:
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def _safe_name(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]", "_", value)[:80] or "item"


def _ext_of(modal: dict) -> str:
    ext = str(modal.get("ext") or "").lower()
    if ext.startswith(".") and len(ext) <= 8:
        return ext
    url = str(modal.get("url") or "").split("?")[0].split("#")[0]
    suffix = Path(url).suffix.lower()
    return suffix if suffix else ".bin"


def _download(url: str, dest: Path, max_bytes: int = 512 * 1024 * 1024) -> None:
    """流式下载上游媒体到临时文件；超限直接失败，避免占满共享卷。"""
    import httpx
    dest.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    try:
        with httpx.Client(follow_redirects=True, timeout=60) as client:
            with client.stream("GET", url) as resp:
                resp.raise_for_status()
                with open(dest, "wb") as f:
                    for chunk in resp.iter_bytes(256 * 1024):
                        written += len(chunk)
                        if written > max_bytes:
                            raise OperatorError(f"上游媒体超过 {max_bytes // (1024 * 1024)}MB: {url}")
                        f.write(chunk)
    except OperatorError:
        dest.unlink(missing_ok=True)
        raise
    except Exception as e:
        dest.unlink(missing_ok=True)
        raise OperatorError(f"上游媒体下载失败 {url}: {e}") from e
    if written == 0:
        dest.unlink(missing_ok=True)
        raise OperatorError(f"上游媒体为空: {url}")


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
        if (src_dir / "manifest.jsonl").exists():
            return self._parse_manifest(src_dir, task_out, unique_id)
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
                    it["sourceFile"] = self.relpath(f)
                    # 音频切片/视频帧产物同样输出逻辑路径，供后端直接挂载还原
                    if "path" in it.get("locator", {}):
                        it["locator"]["path"] = self.relpath(it["locator"]["path"])
                    it["itemId"] = _stable_id(it)
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

    def _parse_manifest(self, src_dir: Path, task_out: Path, unique_id: str) -> dict:
        """manifest 模式：媒体按 URL 临时下载 → 解析 → 删原始文件，sourceFile 记 URL。"""
        entries = []
        for lineno, raw in enumerate((src_dir / "manifest.jsonl").read_text(encoding="utf-8").splitlines(), 1):
            if not raw.strip():
                continue
            try:
                entries.append(json.loads(raw))
            except json.JSONDecodeError as e:
                raise OperatorError(f"manifest.jsonl 第 {lineno} 行不是合法 JSON: {e}")
        if not entries:
            raise OperatorError(f"manifest.jsonl 为空: {src_dir}")

        source_dir = task_out / "_source"
        derived_dir = task_out / "_derived"
        items_path = task_out / "items.jsonl"
        lineage_path = task_out / "lineage.jsonl"
        count = {"text": 0, "image": 0, "audio": 0, "video": 0}
        skipped: list[dict] = []
        total_items = 0

        with open(items_path, "w", encoding="utf-8") as fi, \
                open(lineage_path, "w", encoding="utf-8") as fl:
            for entry in entries:
                item_id = str(entry.get("itemId") or "").strip()
                modals = entry.get("modals") if isinstance(entry.get("modals"), list) else []
                if not item_id or not 1 <= len(modals) <= 4:
                    skipped.append({"itemId": item_id, "reason": "条目缺少 itemId 或 modals 非 1-4 项"})
                    continue
                safe_id = _safe_name(item_id)
                item_count = 0
                mods: list[str] = []
                for idx, modal in enumerate(modals):
                    modality = str(modal.get("type") or "")
                    if modality not in count:
                        skipped.append({"itemId": item_id, "reason": f"未知模态 {modality}"})
                        continue
                    iid = item_id if len(modals) == 1 else f"{item_id}#{idx}"
                    try:
                        if modality == "text":
                            content = str(modal.get("content") or "").strip()
                            if not content:
                                raise OperatorError("文本条目 content 为空")
                            items = [{"modality": "text", "text": content,
                                      "locator": {"segment": idx, "charStart": 0}}]
                            for it in items:
                                it["itemId"] = iid
                                it["sourceFile"] = item_id
                        else:
                            url = str(modal.get("url") or "")
                            if not url.startswith(("http://", "https://")):
                                raise OperatorError("媒体条目缺少 http(s) url")
                            local = source_dir / f"{safe_id}_{idx}{_ext_of(modal)}"
                            _download(url, local)
                            items = self._parse_media(modality, local, derived_dir / safe_id)
                            for n, it in enumerate(items):
                                it["sourceFile"] = url
                                if len(items) > 1:
                                    it["itemId"] = _stable_id(it)
                                else:
                                    it["itemId"] = iid
                                it["locator"]["path"] = self.relpath(it["locator"]["path"])
                            # 图片保留到 404 向量化后清理；音视频解析完即可删原始文件
                            if modality in ("audio", "video"):
                                local.unlink(missing_ok=True)
                    except OperatorError as e:
                        skipped.append({"itemId": item_id, "modality": modality, "reason": str(e)})
                        continue
                    count[modality] += len(items)
                    item_count += len(items)
                    mods.append(modality)
                    for it in items:
                        fi.write(json.dumps(it, ensure_ascii=False) + "\n")
                if item_count:
                    total_items += 1
                fl.write(json.dumps({
                    "uniqueId": unique_id,
                    "operatorId": self.operator_id,
                    "sourceFile": item_id,
                    "modalities": mods,
                    "itemCount": item_count,
                }, ensure_ascii=False) + "\n")

        if sum(count.values()) == 0:
            raise OperatorError(f"manifest 条目全部解析失败: {src_dir}")
        return {
            "mode": "manifest",
            "outputFile": str(items_path),
            "lineageFile": str(lineage_path),
            "itemCount": sum(count.values()),
            "manifestItemCount": total_items,
            "byModality": count,
            "skipped": skipped,
        }

    @staticmethod
    def _parse_media(modality: str, local: Path, out_dir: Path) -> list[dict]:
        if modality == "image":
            return [{"modality": "image", "locator": {"path": str(local)}}]
        if modality == "audio":
            return _parse_audio(local, out_dir)
        if modality == "video":
            return _parse_video(local, out_dir)
        raise OperatorError(f"未知模态 {modality}")
