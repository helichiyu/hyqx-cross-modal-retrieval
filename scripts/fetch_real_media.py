"""获取真实测试数据 v2：THUCNews 新闻正文 + UCF101 真实动作视频（公开正经数据集）。

产出（写入 dataset/cmr_testset/，仓库可提交）：
- text/txt_{001..100}.txt     THUCNews 测试分片（新浪真实新闻正文，10 类轮询抽样，整句截断 ≤480 字）
- videos/video_{01..20}.mp4   UCF101 子集（真实拍摄动作视频，20 个动作类各 1 条，转码 h264）
- 更新 metadata.json 的 count，重写 cmr_testset_ATTRIBUTION.md 的 v2 来源段

来源（均经 hf-mirror.com 镜像下载，缓存 .tmp_fetch/ 可断点续跑）：
- 文本 seamew/THUCNewsText：THUCNews，清华大学整理的新浪网 2005-2011 真实新闻
- 视频 sayakpaul/ucf101-subset：UCF101，UCF CRCV 发布的真实 YouTube 动作视频

用法：
    .venv/Scripts/python scripts/fetch_real_media.py --only text
    .venv/Scripts/python scripts/fetch_real_media.py --only video --videos 20
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tarfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "dataset" / "cmr_testset"
TMP = ROOT / ".tmp_fetch"
ATTR = ROOT / "dataset" / "cmr_testset_ATTRIBUTION.md"
V2_MARKER = "## 真实文本与真实视频（v2"  # 旧 v2 段落起始标记，重跑时截断重写

# ---------------------------------------------------------------- 源定义

THUC_REPO = "seamew/THUCNewsText"
THUC_FILE = "thuc_news_text-test.arrow"
THUC_CACHE = TMP / "thucnews_test.arrow"
THUC_LABELS = {  # dataset_info.json ClassLabel 顺序 → 中文名
    "0": "education/教育", "1": "entertainment/娱乐", "2": "fashion/时尚",
    "3": "finance/财经", "4": "game/游戏", "5": "politic/时政",
    "6": "society/社会", "7": "sport/体育", "8": "stock/股票",
    "9": "technology/科技",
}

UCF_REPO = "quchenyuan/UCF101-ZIP"  # 整包 zip 经 Range 按类抽取（见 fetch_ucf101_zip_range.py）
UCF_TARBALL = TMP / "UCF101_subset.tar.gz"
UCF_EXDIR = TMP / "ucf101_subset"
UCF_URL = f"https://hf-mirror.com/datasets/{UCF_REPO}/resolve/main/UCF101_subset.tar.gz"

# 强繁体指示字：出现即判繁体丢弃（语料本身是简体，这只是防漏网）
TRADITIONAL_MARKERS = set(
    "們這沒說時為與會來後裡開關長樂萬愛書車馬鳥龍鳳廣東業義兩單豐麗麼語讀風飛飯館廳醫藥錢銀縣區禮願額顧鮮騰齊備變讓證調謀貨貼費貴買質輛轉輕邊遠遜適遲選遺還邏鐵錄鎖鏡閉閱閒陽階際隨隱雲電需靜頁項順須頗領頻題顏")


def curl_download(url: str, dst: Path) -> None:
    """curl 下载（断点续传 + 重试）；hf-mirror 直连稳定。"""
    dst.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(1, 5):
        r = subprocess.run(
            ["curl", "-sS", "--ssl-no-revoke", "-L", "-C", "-", "--retry", "8",
             "--retry-delay", "5", "--max-time", "900", "-o", str(dst), url],
            capture_output=True, text=True, encoding="utf-8", errors="replace")
        if r.returncode == 0 and dst.exists() and dst.stat().st_size > 0:
            return
        print(f"  [retry {attempt}/4] {dst.name}: rc={r.returncode} {r.stderr.strip()[:200]}")
        time.sleep(min(10 * attempt, 40))
    raise SystemExit(f"下载失败: {url}")


def _ffmpeg() -> str:
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


# ---------------------------------------------------------------- 文本（THUCNews）

def load_thucnews() -> list[dict]:
    """下载（或复用缓存）THUCNews 测试分片并解析为 [{label, text}]。"""
    if not (THUC_CACHE.exists() and THUC_CACHE.stat().st_size == 25_322_056):
        curl_download(f"https://hf-mirror.com/datasets/{THUC_REPO}/resolve/main/{THUC_FILE}",
                      THUC_CACHE)
    import pyarrow as pa
    try:
        table = pa.ipc.open_stream(pa.memory_map(str(THUC_CACHE))).read_all()
    except pa.ArrowInvalid:
        table = pa.ipc.open_file(str(THUC_CACHE)).read_all()
    rows = [{"label": str(r["label"]), "text": str(r["text"] or "")}
            for r in table.to_pylist() if r["text"] and str(r["label"]) != "4"]  # 剔除游戏类（多为运营通稿）
    print(f"[text] THUCNews test 分片载入 {len(rows)} 条")
    return rows


def clip_sentences(s: str, limit: int = 480, floor: int = 80) -> str:
    """超长新闻正文截到整句边界；截不出足够长度的句子就丢弃。"""
    s = re.sub(r"[ \t]+", " ", s.strip())
    if len(s) <= limit:
        clipped = s
    else:
        cut = s[:limit + 1]
        idx = max(cut.rfind("。"), cut.rfind("！"), cut.rfind("？"), cut.rfind("；"))
        if idx < floor:
            return ""
        clipped = cut[:idx + 1]
    return clipped if len(clipped) >= floor else ""


def is_simplified_prose(s: str) -> bool:
    """简体过滤：无繁体指示字、英文占比低、中文为主、长度适中。"""
    if not 80 <= len(s) <= 500:
        return False
    if TRADITIONAL_MARKERS & set(s):
        return False
    latin = sum(1 for ch in s if "a" <= ch <= "z" or "A" <= ch <= "Z")
    if latin / max(len(s), 1) > 0.08:
        return False
    cjk = sum(1 for ch in s if "一" <= ch <= "鿿")
    return cjk / max(len(s), 1) >= 0.5


def build_texts(want: int) -> list[dict]:
    (OUT / "text").mkdir(parents=True, exist_ok=True)
    for old in (OUT / "text").glob("txt_*.txt"):
        old.unlink()
    rows = load_thucnews()
    by_label: dict[str, list[str]] = {}
    for r in rows:
        by_label.setdefault(r["label"], []).append(r["text"])

    entries: list[dict] = []
    used: set[str] = set()
    cursors = {k: 0 for k in by_label}
    labels = sorted(by_label)
    # 轮询类别取材，保证 10 个新闻类别均衡覆盖
    while len(entries) < want and any(cursors[k] < len(by_label[k]) for k in labels):
        progressed = False
        for k in labels:
            if len(entries) >= want:
                break
            texts = by_label[k]
            while cursors[k] < len(texts):
                cursors[k] += 1
                text = clip_sentences(texts[cursors[k] - 1])
                if not text or not is_simplified_prose(text):
                    continue
                key = text[:50]
                if key in used:
                    continue
                used.add(key)
                progressed = True
                name = f"txt_{len(entries) + 1:03d}.txt"
                (OUT / "text" / name).write_text(text + "\n", encoding="utf-8")
                entries.append({"file": f"text/{name}",
                                "category": THUC_LABELS.get(k, k)})
                break
        if not progressed:
            break
    if len(entries) < want:
        raise SystemExit(f"合格文本只有 {len(entries)} 条，少于 {want}")
    dist = {}
    for e in entries:
        dist[e["category"]] = dist.get(e["category"], 0) + 1
    print(f"[text] 完成 {len(entries)} 条，分布 {dist}")
    return entries


# ---------------------------------------------------------------- 视频（UCF101）

def ensure_ucf101() -> list[Path]:
    """下载（或复用）UCF101 子集 tar 包并解压，返回全部 .avi 路径。"""
    marker = UCF_EXDIR / ".extracted"
    if not marker.exists():
        if not (UCF_TARBALL.exists() and UCF_TARBALL.stat().st_size == 171_386_880):
            curl_download(UCF_URL, UCF_TARBALL)
        print(f"[video] 解压 {UCF_TARBALL.name} ...")
        with tarfile.open(UCF_TARBALL, "r:*") as tar:  # 该包实际是未压缩 tar，自动探测
            tar.extractall(UCF_EXDIR, filter="data")
        marker.write_text("ok", encoding="utf-8")
    avis = sorted(UCF_EXDIR.rglob("*.avi"))
    if not avis:
        raise SystemExit("UCF101 子集里没有 .avi 文件")
    print(f"[video] UCF101 子集载入 {len(avis)} 个 .avi")
    return avis


def transcode(src: Path, dst: Path, seconds: int = 12, width: int = 640) -> bool:
    ff = _ffmpeg()
    cmd = [ff, "-y", "-loglevel", "error", "-i", str(src), "-t", str(seconds),
           "-vf", f"scale='min({width},iw)':-2", "-c:v", "libx264", "-crf", "27",
           "-preset", "veryfast", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
           "-an", str(dst)]  # UCF101 无音轨，直接 -an
    if subprocess.run(cmd, capture_output=True).returncode != 0:
        return False
    return dst.exists() and dst.stat().st_size > 8_000  # 320x240 短片段 crf27 可低至 ~15KB


def build_videos(want: int) -> list[dict]:
    (OUT / "videos").mkdir(parents=True, exist_ok=True)
    for old in (OUT / "videos").glob("video_*.mp4"):  # 替换旧的合成视频
        old.unlink()
    avis = ensure_ucf101()
    by_class: dict[str, list[Path]] = {}
    for p in avis:
        by_class.setdefault(p.parent.name, []).append(p)
    for k in by_class:
        by_class[k].sort(key=lambda p: p.stat().st_size)  # 每类优先取较短片段

    entries: list[dict] = []
    classes = sorted(by_class)
    max_depth = max(len(v) for v in by_class.values())
    # 第 depth 轮每类取第 depth+1 条：先保证类别覆盖，不够 20 再逐轮加深
    for depth in range(max_depth):
        for cls in classes:
            if len(entries) >= want:
                break
            if depth >= len(by_class[cls]):
                continue
            cand = by_class[cls][depth]
            dst = OUT / "videos" / f"video_{len(entries) + 1:02d}.mp4"
            if transcode(cand, dst):
                entries.append({"file": f"videos/{dst.name}", "ucf_class": cls,
                                "origin": cand.name})
                print(f"[video] {len(entries)}/{want} {dst.name} "
                      f"{dst.stat().st_size // 1024}KB ← {cls}/{cand.name}")
            else:
                print(f"[video] 转码失败，跳过: {cls}/{cand.name}")
        if len(entries) >= want:
            break
    if len(entries) < want:
        raise SystemExit(f"成功转码 {len(entries)} 段，少于 {want}（类别数 {len(classes)}）")
    return entries


# ---------------------------------------------------------------- 收尾

def refresh_metadata() -> None:
    total = sum(1 for d in (OUT / "images", OUT / "audio", OUT / "videos", OUT / "text")
                if d.exists() for _ in d.iterdir())
    meta_path = OUT / "metadata.json"
    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    meta.update({"count": total, "version": "v2"})
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"[meta] count={total} → {meta_path}")


def write_attribution(text_entries: list[dict], video_entries: list[dict]) -> None:
    body = ATTR.read_text(encoding="utf-8") if ATTR.exists() else ""
    cut = body.find(V2_MARKER)
    if cut >= 0:  # 重跑：截断旧的 v2 段后重写
        body = body[:cut].rstrip() + "\n"

    lines = ["", "## 真实文本与真实视频（v2，2026-11-12 重抓）", ""]
    if text_entries:
        dist = {}
        for e in text_entries:
            dist[e["category"]] = dist.get(e["category"], 0) + 1
        lines += [
            "### 文本：THUCNews 新闻正文（真实新闻数据集）", "",
            f"- 来源：`seamew/THUCNewsText`（HuggingFace 镜像下载，test 分片，抽样 {len(text_entries)} 条）",
            "- 原始出处：THUCNews —— 清华大学自然语言处理实验室整理的新浪网真实新闻（2005-2011），",
            f"  共 {len(dist)} 个类别：" + "、".join(f"{k}×{v}" for k, v in sorted(dist.items())),
            "- 处理：按类别轮询均衡抽样；超长正文在整句边界截断（≤480 字）；仅保留简体、以中文为主的条目",
            "- 许可：THUCNews 面向学术研究发布；本仓库仅作内部检索链路测试，不对外再分发", "",
        ]
    if video_entries:
        lines += [
            "### 视频：UCF101 真实动作视频（真实拍摄数据集）", "",
            f"- 来源：`{UCF_REPO}`（HuggingFace 镜像，抽取 {len(video_entries)} 条）",
            "- 原始出处：UCF101 —— 美国中佛罗里达大学 CRCV 发布的真实 YouTube 动作视频数据集",
            " （Khurram Soomro et al., *UCF101: A Dataset of 101 Human Action Classes From Videos in The Wild*, 2012）",
            "- 抽取：整包 zip 经 HTTP Range 定位抽 20 个动作类各 1 条（`scripts/fetch_ucf101_zip_range.py`）",
            "- 处理：转码为 h264 MP4（≤12s、≤640px、去音轨）",
            "- 许可：UCF101 供研究使用；本仓库仅作内部检索链路测试，不对外再分发", "",
            "逐条对应：", "",
        ]
        for e in video_entries:
            lines.append(f"- `{e['file']}` — {e['ucf_class']}（来源 {e['origin']}）")
        lines.append("")
    ATTR.write_text(body.rstrip() + "\n" + "\n".join(lines) + "\n", encoding="utf-8")
    print(f"[attr] 重写 v2 来源段：{len(text_entries)} 文本 + {len(video_entries)} 视频 → {ATTR.name}")


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser()
    ap.add_argument("--texts", type=int, default=100)
    ap.add_argument("--videos", type=int, default=20)
    ap.add_argument("--only", choices=["text", "video"], default=None)
    args = ap.parse_args()
    TMP.mkdir(exist_ok=True)
    text_entries: list[dict] = []
    video_entries: list[dict] = []
    if args.only in (None, "text"):
        text_entries = build_texts(args.texts)
        (TMP / "entries_text.json").write_text(json.dumps(text_entries, ensure_ascii=False), encoding="utf-8")
    if args.only in (None, "video"):
        video_entries = build_videos(args.videos)
        (TMP / "entries_video.json").write_text(json.dumps(video_entries, ensure_ascii=False), encoding="utf-8")
    # 分阶段跑时从缓存合并，保证 attribution 两段齐全
    if not text_entries and (TMP / "entries_text.json").exists():
        text_entries = json.loads((TMP / "entries_text.json").read_text(encoding="utf-8"))
    if not video_entries and (TMP / "entries_video.json").exists():
        video_entries = json.loads((TMP / "entries_video.json").read_text(encoding="utf-8"))
    refresh_metadata()
    write_attribution(text_entries, video_entries)
    print("完成")


if __name__ == "__main__":
    main()
