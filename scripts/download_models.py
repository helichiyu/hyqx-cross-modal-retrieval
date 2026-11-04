"""下载模型权重到 config.yaml 的 paths.model_root（本地默认 D:/models）。

优先 ModelScope（国内快），失败回退 hf-mirror。用法：python scripts/download_models.py
"""

import os
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
CFG = yaml.safe_load((ROOT / "config.yaml").read_text(encoding="utf-8"))
MODEL_ROOT = Path(CFG["paths"]["model_root"])

MODELS = [
    # (本地目录名, HF id, ModelScope id 或 None)
    (CFG["models"]["chinese_clip"], "OFA-Sys/chinese-clip-vit-base-patch16", "AI-ModelScope/chinese-clip-vit-base-patch16"),
    (CFG["models"]["clap"], "laion/clap-htsat-fused", None),
    (CFG["models"]["reranker"], "BAAI/bge-reranker-v2-m3", "BAAI/bge-reranker-v2-m3"),
]


def from_modelscope(ms_id: str, target: Path) -> bool:
    try:
        from modelscope import snapshot_download as ms_download
        ms_download(ms_id, local_dir=str(target))
        return True
    except Exception as e:
        print(f"  modelscope 失败: {e}")
        return False


def from_hf_mirror(hf_id: str, target: Path) -> bool:
    try:
        os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"
        from huggingface_hub import snapshot_download
        snapshot_download(hf_id, local_dir=str(target))
        return True
    except Exception as e:
        print(f"  hf-mirror 失败: {e}")
        return False


def main() -> int:
    MODEL_ROOT.mkdir(parents=True, exist_ok=True)
    ok = True
    for name, hf_id, ms_id in MODELS:
        target = MODEL_ROOT / name
        marker = target / "config.json"
        if marker.exists():
            print(f"[skip] {name} 已存在")
            continue
        print(f"[下载] {name} <- {ms_id or hf_id}")
        done = from_modelscope(ms_id, target) if ms_id else False
        done = done or from_hf_mirror(hf_id, target)
        if not done or not marker.exists():
            print(f"[失败] {name}")
            ok = False
        else:
            print(f"[完成] {name}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
