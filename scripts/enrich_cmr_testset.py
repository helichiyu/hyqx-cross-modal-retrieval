"""为跨模态检索测试集补充更丰富的演示素材。

只做增量补充，不重新下载原始数据：
- 视频：把原来的 2 秒静态短片重制成 6 秒带推拉运镜的短片，并追加横屏/方形/竖屏多种比例；
- 文本：补充动物、风景、美食、运动、交通、声音、视频、检索说明等多篇中文文档；
- 图片：从已有图片派生方形、竖屏、横屏三种比例变体，方便验证展示框自适应；
- 音频：把已有环境音两两混合成新的声音片段，增加可播放的音频条目。

用法：
    .venv/Scripts/python scripts/enrich_cmr_testset.py
    .venv/Scripts/python scripts/enrich_cmr_testset.py --dataset runtime/cmr_testset
    .venv/Scripts/python scripts/enrich_cmr_testset.py --dataset dataset/cmr_testset --extra-videos 0
"""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

TEXT_DOCS: dict[str, str] = {
    "animals.txt": (
        "动物观察笔记\n"
        "狗在沙滩上奔跑时耳朵会向后贴，尾巴保持上扬；猫伸懒腰前通常先压低前肢，再把背部拱起。"
        "清晨的树梢常有麻雀和喜鹊交替鸣叫，池塘边的青蛙在雨后叫得最响。"
        "牧场里的奶牛低头吃草，脖子上的铃铛会随着步伐发出低沉的回声。"
        "这些画面和声音常被用作跨模态检索的测试样本，用来检查文本能否同时召回图像、音频和视频。\n"
    ),
    "scenery.txt": (
        "自然与城市风景\n"
        "海浪拍打礁石时会在岸边留下白色泡沫，远处的海平线与天空连成一条淡蓝色的线。"
        "山谷里的晨雾沿着山脊缓慢移动，阳光穿过云层后照亮成片的松林。"
        "城市夜景中，高架桥的车流形成连续的光带，路口的红绿灯在湿润的路面上留下倒影。"
        "同一处风景换个角度拍摄，画面的长宽比和构图会明显不同，检索系统需要能适应这些差异。\n"
    ),
    "food.txt": (
        "日常饮食记录\n"
        "一碗热汤面通常由清汤、面条、青菜和少量葱花组成，端上桌时还会冒着热气。"
        "水果摊上堆着苹果、橙子、香蕉和葡萄，颜色从深红到浅黄各不相同。"
        "手冲咖啡的水流细而稳定，滤杯里的咖啡粉会慢慢鼓起；刚出炉的面包表皮酥脆，切开后能看到柔软的气孔。"
        "这些食物图片适合用来测试以文搜图，也适合检查图片在方形和竖屏展示框里的裁切效果。\n"
    ),
    "sports.txt": (
        "运动场景说明\n"
        "足球比赛中，守门员在禁区里横向移动，前锋带球突破后起脚射门。"
        "晨跑的人沿着河堤前进，呼吸节奏与步频保持一致；骑行队伍在弯道处压低身体，车轮快速掠过路面。"
        "泳池里的自由泳选手交替划水，转身时在池壁蹬出一串气泡。"
        "运动视频包含连续的位移和姿态变化，抽帧后同一段视频会有多个相似画面，展示时应按素材去重。\n"
    ),
    "transport.txt": (
        "交通出行片段\n"
        "早高峰的地铁站里，乘客在屏蔽门前排队，列车进站时带起一阵风。"
        "公交车靠站后车门打开，乘客依次上下车；绿皮火车沿着铁轨缓慢驶过田野。"
        "飞机起飞前在跑道上加速，机翼后缘的襟翼逐渐收起。"
        "交通场景既有静态照片，也有短视频和站台环境音，适合检验图文、文音、文视三类检索链路。\n"
    ),
    "soundscape.txt": (
        "环境声音清单\n"
        "本测试集包含狗叫、猫叫、公鸡打鸣、猪哼叫、奶牛低鸣、青蛙鸣叫、母鸡咯咯叫、绵羊咩叫、乌鸦叫和昆虫振翅十类动物环境音。"
        "每条音频都转为 16kHz 单声道，便于统一送入音频编码器。"
        "混合片段由两条环境音叠加而成，例如狗叫与虫鸣同时出现、海浪与鸟鸣同时出现，用来覆盖更复杂的声音场景。"
        "检索音频时既可以用英文类别描述，也可以用中文场景描述，系统会返回相似度最高的声音片段。\n"
    ),
    "video_scenes.txt": (
        "视频素材说明\n"
        "演示视频由测试集中的图片合成，每段 6 秒、12 帧每秒，并加入缓慢推近、平移或拉远运镜，"
        "避免整段画面完全静止。横屏视频适合展示宽幅风景，方形视频适合展示主体特写，竖屏视频适合展示人物或建筑。"
        "播放器应保持宽度不变、高度随视频比例变化，不能露出固定展示框的底色。"
        "同一段视频按 1 帧每秒抽帧后会产生多条索引条目，结果页需要按源文件合并展示。\n"
    ),
    "retrieval_guide.txt": (
        "跨模态检索使用说明\n"
        "在检索台输入中文描述后，选择查询模态与目标模态，再选择检索策略即可发起检索。"
        "查询词过短或存在歧义时，系统会给出改写建议，可以选择候选继续检索，也可以仍按原词检索。"
        "结果页支持按模态分组、按相似度阈值过滤、按来源筛选，并可以收藏、点赞或点踩。"
        "图片、视频、音频和文本在结果卡中使用不同的预览方式：图片和视频按比例自适应高度，"
        "音频只显示播放条，文本按内容长度伸缩并限制最大高度。\n"
    ),
}


def _ffmpeg() -> str:
    import imageio_ffmpeg

    return imageio_ffmpeg.get_ffmpeg_exe()


def _run(args: list[str]) -> None:
    proc = subprocess.run(
        [_ffmpeg(), "-y", "-hide_banner", "-loglevel", "error", *args],
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        raise SystemExit(f"ffmpeg failed: {proc.stderr.strip()[:500]}")


def _video_size(index: int) -> tuple[int, int]:
    """按序号轮换横屏 / 方形 / 竖屏，让展示框能覆盖不同宽高比。"""
    return [(640, 426), (480, 480), (384, 512)][(index - 1) % 3]


def _build_video(image: Path, out: Path, index: int, seconds: int, fps: int) -> None:
    width, height = _video_size(index)
    frames = seconds * fps
    if index % 2 == 0:
        zoom = f"max(1.18-0.18*on/{frames},1.0)"
    else:
        zoom = f"min(1+0.18*on/{frames},1.18)"
    vf = (
        f"scale={width}:{height}:force_original_aspect_ratio=increase,"
        f"crop={width}:{height},setsar=1,"
        f"zoompan=z='{zoom}':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
        f":d=1:s={width}x{height}:fps={fps},format=yuv420p"
    )
    _run([
        "-loop", "1", "-framerate", str(fps), "-i", str(image),
        "-t", str(seconds), "-vf", vf,
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
        "-movflags", "+faststart", str(out),
    ])


def _build_image_variants(image: Path, out_dir: Path) -> int:
    variants = {
        "square": (480, 480),
        "portrait": (360, 480),
        "wide": (640, 360),
    }
    created = 0
    for name, (width, height) in variants.items():
        out = out_dir / f"var_{image.stem}_{name}.jpg"
        vf = (
            f"scale={width}:{height}:force_original_aspect_ratio=increase,"
            f"crop={width}:{height},setsar=1"
        )
        _run(["-i", str(image), "-vf", vf, "-q:v", "3", str(out)])
        created += 1
    return created


def _mix_audio(a: Path, b: Path, out: Path) -> None:
    _run([
        "-i", str(a), "-i", str(b),
        "-filter_complex",
        "[0:a]volume=0.75[left];[1:a]volume=0.6[right];"
        "[left][right]amix=inputs=2:duration=longest:dropout_transition=0,"
        "aresample=16000",
        "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", str(out),
    ])


def _image_sort_key(path: Path) -> tuple[int, object]:
    """数字文件名按数值排序，保证视频序号与评测集 query 顺序一致。

    构建脚本按图像 id 数值升序选图并生成 ``video_XX.mp4``；若这里按文件名字典序
    排序（``1486399999.jpg`` 会排在 ``207015505.jpg`` 前），视频来源就会与
    ``eval_video.jsonl`` 的 query 错位。
    """
    stem = path.stem
    return (0, int(stem)) if stem.isdigit() else (1, stem)


def enrich(dataset: Path, videos: int, extra_videos: int, image_variants: int,
           audio_mixes: int, seconds: int, fps: int) -> None:
    images = sorted((dataset / "images").glob("*.jpg"), key=_image_sort_key)
    audios = sorted((dataset / "audio").glob("*.wav"))
    if not images:
        raise SystemExit(f"未找到图片：{dataset / 'images'}")

    total_videos = videos + extra_videos
    total_videos = min(total_videos, len(images))
    for i in range(1, total_videos + 1):
        image = images[(i - 1) % len(images)]
        out = dataset / "videos" / f"video_{i:02d}.mp4"
        _build_video(image, out, i, seconds, fps)
        print(f"[video] {out.name} <- {image.name} {_video_size(i)} {seconds}s")

    for i in range(1, image_variants + 1):
        _build_image_variants(images[(i - 1) % len(images)], dataset / "images")
    print(f"[image] 派生 {image_variants * 3} 张比例变体")

    if len(audios) >= 2:
        for i in range(1, audio_mixes + 1):
            a = audios[(i - 1) % len(audios)]
            b = audios[(i + 2) % len(audios)]
            out = dataset / "audio" / f"mixed_{i:02d}.wav"
            _mix_audio(a, b, out)
        print(f"[audio] 新增 {audio_mixes} 条混合音频")
    else:
        print("[audio] 音频文件不足 2 条，跳过混合")

    text_dir = dataset / "text"
    text_dir.mkdir(parents=True, exist_ok=True)
    for name, content in TEXT_DOCS.items():
        (text_dir / name).write_text(content, encoding="utf-8")
    print(f"[text] 写入 {len(TEXT_DOCS)} 篇文本")


def main() -> None:
    ap = argparse.ArgumentParser(description="为跨模态检索测试集补充演示素材")
    ap.add_argument("--dataset", default="runtime/cmr_testset", help="数据集目录")
    ap.add_argument("--videos", type=int, default=10, help="重制前 N 段视频为带运镜短片")
    ap.add_argument("--extra-videos", type=int, default=10, help="追加 N 段多种比例视频")
    ap.add_argument("--image-variants", type=int, default=10, help="为前 N 张图派生比例变体")
    ap.add_argument("--audio-mixes", type=int, default=20, help="生成 N 条混合音频")
    ap.add_argument("--seconds", type=int, default=6, help="视频时长（秒）")
    ap.add_argument("--fps", type=int, default=12, help="视频帧率")
    args = ap.parse_args()
    enrich(
        ROOT / args.dataset,
        args.videos,
        args.extra_videos,
        args.image_variants,
        args.audio_mixes,
        args.seconds,
        args.fps,
    )


if __name__ == "__main__":
    main()
