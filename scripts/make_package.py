"""生成交付包：只含 Docker 构建/运行所需内容。

产物 dist/cmr-operators.zip，包含：
  Dockerfile、docker-compose.yml、requirements.txt、config.yaml、map.json、
  router/、operators/（剔除 __pycache__）、README.md（包内说明）。
不含 runtime/、dataset/、docs/、scripts/、tests/（文档与测试数据另行交付）。

用法：python scripts/make_package.py
"""

import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "dist" / "cmr-operators.zip"

FILES = ["Dockerfile", "docker-compose.yml", "requirements.txt", "config.yaml", "map.json"]
DIRS = ["router", "operators"]

PKG_README = """# 跨模态检索中心 · 算子服务交付包

构建并启动（先按《部署说明》第二节把三个模型权重放到 ./weight 下）：

    docker compose up -d --build

验证：

    curl http://127.0.0.1:6300/healthz

- 对外仅 6300 端口；任务与索引持久化在 ./runtime，权重挂载在 ./weight。
- 配置只改 config.yaml（容器内可用环境变量 CMR_MODEL_ROOT 等覆盖路径，见 docker-compose.yml）。
- 9 个算子的接口字段见随附《算子说明书》，调用样例见《接口调用示例》，分工见《前后端对接说明》。
"""


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.unlink()
    n = 0
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("README.md", PKG_README)
        for name in FILES:
            zf.write(ROOT / name, name)
            n += 1
        for d in DIRS:
            for p in sorted((ROOT / d).rglob("*")):
                if p.is_file() and "__pycache__" not in p.parts:
                    zf.write(p, p.relative_to(ROOT))
                    n += 1
    size_mb = OUT.stat().st_size / 1024 / 1024
    print(f"交付包已生成: {OUT}（{n} 个文件，{size_mb:.1f} MB）")
    with zipfile.ZipFile(OUT) as zf:
        tops = sorted({x.split("/")[0] for x in zf.namelist()})
    print("顶层内容:", tops)
    leftover = [x for x in zipfile.ZipFile(OUT).namelist() if "__pycache__" in x]
    if leftover:
        print(f"错误: 包内残留 __pycache__ {len(leftover)} 个", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
