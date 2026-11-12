"""构建跨模态检索测试集（默认生成可提交 git 的小规模子集）。

数据来源（均从 hf-mirror 下载，国内可直连）：
- 图像 + 中文查询 + 相关性标注：tomzhuo/Flickr30K-CN（1000 图 / 5000 查询 / 5000 qrels）
- 环境音 + 英文类别：DynamicSuperb/EnvironmentalSoundClassification_ESC50-Animals（200 条，10 类）
- 视频：由选中的图像经 ffmpeg 合成 2 秒短片，只为覆盖视频抽帧链路

产物：
    <out>/images/*.jpg            图像
    <out>/audio/*.wav             16kHz 单声道音频
    <out>/videos/*.mp4            合成视频
    <out>/text/notes.txt          说明文本
    <out>/metadata.json           401 接入校验声明
    <eval-out>/eval_image.jsonl   中文查询 → 图像
    <eval-out>/eval_audio.jsonl   英文查询 → 音频
    <eval-out>/eval_video.jsonl   中文查询 → 视频
    <eval-out>/eval_all.jsonl     合并评测集

依赖：除项目 requirements.txt 外，还需 `python -m pip install pyarrow`（仅造数据用，运行时不依赖）。

用法：
    .venv/Scripts/python scripts/build_cmr_testset.py
    .venv/Scripts/python scripts/build_cmr_testset.py --images 1000 --audio 200 --videos 10
    .venv/Scripts/python scripts/build_cmr_testset.py --out runtime/full_testset --eval-out runtime/full_testset_eval
"""

from __future__ import annotations

import argparse
import collections
import json
import random
import shutil
import subprocess
import sys
from pathlib import Path

import httpx

from enrich_cmr_testset import TEXT_DOCS, _build_image_variants, _build_video, _mix_audio

ROOT = Path(__file__).resolve().parents[1]
MIRROR = "https://hf-mirror.com"
DATASETS = {
    "flickr_corpus": (
        "tomzhuo/Flickr30K-CN",
        "corpus/corpus-00000-of-00001.parquet",
    ),
    "flickr_query": (
        "tomzhuo/Flickr30K-CN",
        "query/query-00000-of-00001.parquet",
    ),
    "flickr_qrels": (
        "tomzhuo/Flickr30K-CN",
        "qrels/qrels-00000-of-00001.parquet",
    ),
    "esc50_animals": (
        "DynamicSuperb/EnvironmentalSoundClassification_ESC50-Animals",
        "data/test-00000-of-00001-2bd71bc76b348175.parquet",
    ),
}

AUDIO_PROMPTS = {
    "dog": "a dog barking",
    "rooster": "a rooster crowing",
    "pig": "a pig grunting",
    "cow": "a cow mooing",
    "frog": "frogs croaking",
    "cat": "a cat meowing",
    "hen": "a hen clucking",
    "sheep": "a sheep bleating",
    "crow": "a crow cawing",
    "insects": "insects buzzing",
}


def _download(name: str, cache: Path) -> Path:
    repo, path = DATASETS[name]
    dst = cache / f"{name}.parquet"
    if dst.exists() and dst.stat().st_size > 0:
        return dst
    url = f"{MIRROR}/datasets/{repo}/resolve/main/{path}"
    print(f"[download] {url}")
    with httpx.stream("GET", url, timeout=300, follow_redirects=True) as r:
        r.raise_for_status()
        with open(dst, "wb") as f:
            for chunk in r.iter_bytes(1 << 20):
                f.write(chunk)
    return dst


def _ffmpeg() -> str:
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def _read_parquet(path: Path) -> list[dict]:
    try:
        import pyarrow.parquet as pq
    except ImportError as e:
        raise SystemExit("缺少 pyarrow，请先执行：python -m pip install pyarrow") from e
    return pq.read_table(path).to_pylist()


def _write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n",
        encoding="utf-8",
    )


def build(out: Path, eval_out: Path, cache: Path, n_images: int, n_audio: int, n_videos: int,
          video_seconds: int = 6, fps: int = 12, image_variants: int = 10,
          audio_mixes: int = 20) -> None:
    if out.exists():
        shutil.rmtree(out)
    for sub in ("images", "audio", "videos", "text"):
        (out / sub).mkdir(parents=True, exist_ok=True)

    # ---- 图像 + 中文查询 + qrels ----
    corpus = _read_parquet(_download("flickr_corpus", cache))
    queries = {r["id"]: r["text"] for r in _read_parquet(_download("flickr_query", cache))}
    qrels = _read_parquet(_download("flickr_qrels", cache))

    queries_by_image: dict[int, list[int]] = collections.defaultdict(list)
    for r in qrels:
        queries_by_image[r["corpus-id"]].append(r["query-id"])

    candidates = sorted(
        r["id"] for r in corpus
        if r["id"] in queries_by_image and any(q in queries for q in queries_by_image[r["id"]])
    )
    if n_images > len(candidates):
        raise SystemExit(f"可用图像只有 {len(candidates)} 张，少于 --images {n_images}")
    chosen_ids = candidates[:n_images]
    corpus_by_id = {r["id"]: r for r in corpus}
    image_queries: dict[int, str] = {}
    for image_id in chosen_ids:
        (out / "images" / f"{image_id}.jpg").write_bytes(corpus_by_id[image_id]["image"]["bytes"])
        qid = next(q for q in queries_by_image[image_id] if q in queries)
        image_queries[image_id] = queries[qid]

    # ---- 音频（转 16kHz 单声道，控制仓库体积） ----
    esc = _read_parquet(_download("esc50_animals", cache))
    by_label: dict[str, list[dict]] = collections.defaultdict(list)
    for r in esc:
        by_label[r["label"]].append(r)
    for rows in by_label.values():
        rows.sort(key=lambda r: r["file"])

    picked: list[dict] = []
    labels = sorted(by_label)
    idx = 0
    while len(picked) < n_audio and any(idx < len(by_label[l]) for l in labels):
        for label in labels:
            rows = by_label[label]
            if idx < len(rows) and len(picked) < n_audio:
                picked.append(rows[idx])
        idx += 1
    ff = _ffmpeg()
    for r in picked:
        src = out / "audio" / f"_tmp_{r['file']}"
        src.write_bytes(r["audio"]["bytes"])
        dst = out / "audio" / r["file"]
        subprocess.run(
            [ff, "-y", "-hide_banner", "-loglevel", "error", "-i", str(src),
             "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", str(dst)],
            check=True,
        )
        src.unlink()

    # ---- 视频：用前 n_videos 张图合成带推拉运镜的短片，横屏/方形/竖屏轮换 ----
    n_videos = min(n_videos, len(chosen_ids))
    video_queries: list[tuple[str, str]] = []
    for i, image_id in enumerate(chosen_ids[:n_videos], 1):
        name = f"video_{i:02d}.mp4"
        _build_video(out / "images" / f"{image_id}.jpg", out / "videos" / name, i, video_seconds, fps)
        video_queries.append((image_queries[image_id], name))

    # ---- 图片比例变体：覆盖方形 / 竖屏 / 横屏，便于验证展示框自适应 ----
    for j in range(min(image_variants, len(chosen_ids))):
        _build_image_variants(out / "images" / f"{chosen_ids[j]}.jpg", out / "images")

    # ---- 混合音频：两条环境音叠加，补充可播放的音频条目 ----
    audio_files = [out / "audio" / r["file"] for r in picked]
    for k in range(min(audio_mixes, len(audio_files) // 2)):
        _mix_audio(audio_files[k], audio_files[k + 1], out / "audio" / f"mixed_{k + 1:02d}.wav")

    # ---- 文本 + metadata ----
    (out / "text" / "notes.txt").write_text(
        "跨模态检索测试集说明\n"
        "图像与中文查询来自 Flickr30K-CN；音频来自 ESC-50 动物类；视频由图像合成。\n"
        "仅用于算子链路与指标回归测试，不代表真实业务数据分布。\n",
        encoding="utf-8",
    )
    for text_name, content in TEXT_DOCS.items():
        (out / "text" / text_name).write_text(content, encoding="utf-8")
    text_count = len(TEXT_DOCS) + 1
    file_count = sum(
        1 for p in out.rglob("*")
        if p.is_file() and p.name != "metadata.json"
    )
    (out / "metadata.json").write_text(
        json.dumps({
            "name": "cmr-testset-flickr30kcn-esc50",
            "version": "v1",
            "count": file_count,
            "modalities": ["text", "image", "audio", "video"],
            "scenes": ["cross-modal-retrieval-test"],
        }, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    # ---- 评测集 ----
    eval_image = [
        {"query": image_queries[i], "answers": [f"{i}.jpg"]} for i in chosen_ids
    ]
    eval_audio = [
        {"query": AUDIO_PROMPTS.get(r["label"], f"{r['label']} sound"), "answers": [r["file"]]}
        for r in picked
    ]
    eval_video = [{"query": q, "answers": [name]} for q, name in video_queries]
    _write_jsonl(eval_out / "eval_image.jsonl", eval_image)
    _write_jsonl(eval_out / "eval_audio.jsonl", eval_audio)
    _write_jsonl(eval_out / "eval_video.jsonl", eval_video)
    _write_jsonl(eval_out / "eval_all.jsonl", eval_image + eval_audio + eval_video)

    print(f"[done] dataset={out} files={file_count}")
    print(f"       images={len(chosen_ids)} audio={len(picked)} videos={n_videos} text=1")
    print(f"[done] eval={eval_out} image={len(eval_image)} audio={len(eval_audio)} video={len(eval_video)}")


def main() -> None:
    ap = argparse.ArgumentParser(description="构建跨模态检索测试集")
    ap.add_argument("--out", default="dataset/cmr_testset", help="数据集目录")
    ap.add_argument("--eval-out", default="dataset/cmr_testset_eval", help="评测集目录")
    ap.add_argument("--cache", default="runtime/_downloads/cmr_testset", help="下载缓存目录")
    ap.add_argument("--images", type=int, default=100)
    ap.add_argument("--audio", type=int, default=50)
    ap.add_argument("--videos", type=int, default=10)
    ap.add_argument("--video-seconds", type=int, default=6, help="合成视频时长（秒）")
    ap.add_argument("--fps", type=int, default=12, help="合成视频帧率")
    ap.add_argument("--image-variants", type=int, default=10, help="为前 N 张图派生比例变体")
    ap.add_argument("--audio-mixes", type=int, default=20, help="生成 N 条混合音频")
    ap.add_argument("--seed", type=int, default=42, help="保留参数；当前按 id 稳定选择")
    args = ap.parse_args()

    random.seed(args.seed)
    build(
        ROOT / args.out,
        ROOT / args.eval_out,
        ROOT / args.cache,
        args.images,
        args.audio,
        args.videos,
        args.video_seconds,
        args.fps,
        args.image_variants,
        args.audio_mixes,
    )


if __name__ == "__main__":
    main()
