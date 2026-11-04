"""生成 demo 样本：1 文本 + 1 图片 + 1 音频(65s→3片) + 1 视频(6s)。

用法：python scripts/make_samples.py
"""

from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
SAMPLE = ROOT / "dataset" / "sample"


def make_text() -> None:
    content = (
        "一只金毛犬在沙滩上奔跑，海浪拍打着岸边，阳光明媚。\n"
        "播客录音：今天我们聊聊睡眠。深度睡眠对记忆巩固非常重要，"
        "成年人每天需要七到九小时的睡眠，睡前一小时不要看屏幕。\n"
        "租赁合同第十一条：租户不得在房屋内饲养宠物，违者押金不退。\n"
        "错误码 PAYMENT_DECLINED 表示银行卡扣款失败，请检查余额后重试。\n"
    ) * 3
    (SAMPLE / "notes.txt").write_text(content, encoding="utf-8")


def make_image() -> None:
    from PIL import Image, ImageDraw
    img = Image.new("RGB", (640, 400), (135, 206, 235))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 300, 640, 400], fill=(238, 214, 175))   # 沙滩
    d.ellipse([480, 40, 580, 140], fill=(255, 220, 60))     # 太阳
    d.ellipse([120, 240, 260, 320], fill=(180, 120, 60))    # 狗身子(示意)
    img.save(SAMPLE / "dog_beach.jpg", quality=90)


def make_audio() -> None:
    import soundfile as sf
    sr = 48000
    t = np.linspace(0, 65, int(sr * 65), endpoint=False)
    wave = 0.2 * np.sin(2 * np.pi * 220 * t) * (1 + 0.3 * np.sin(2 * np.pi * 0.1 * t))
    sf.write(SAMPLE / "podcast.wav", wave.astype(np.float32), sr)


def make_video() -> None:
    import imageio_ffmpeg
    import subprocess
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    # 6 秒测试视频：色块滚动 + 1kHz 音轨
    subprocess.run([
        ffmpeg, "-y", "-loglevel", "error",
        "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=25:duration=6",
        "-f", "lavfi", "-i", "sine=frequency=1000:duration=6",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac",
        str(SAMPLE / "clip.mp4"),
    ], check=True)


if __name__ == "__main__":
    SAMPLE.mkdir(parents=True, exist_ok=True)
    make_text()
    make_image()
    make_audio()
    make_video()
    print("sample files:")
    for p in sorted(SAMPLE.iterdir()):
        print(f"  {p.name}  {p.stat().st_size} bytes")
