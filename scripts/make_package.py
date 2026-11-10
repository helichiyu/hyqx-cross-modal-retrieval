"""生成交付包：只含算子服务的 Docker 构建/运行内容。

交付根目录为 operators/（含 Dockerfile、docker-compose.yml、config.yaml、map.json、
requirements.txt、router/、common/ 与 9 个算子目录），压缩包内保持同样的顶层结构。

产物 dist/cmr-operators.zip；不含 weight/、runtime/、dataset/、docs/、scripts/、tests/
（模型权重、运行产物、文档与测试数据另行交付）。

用法：python scripts/make_package.py
"""

import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE_ROOT = ROOT / "operators"
OUT = ROOT / "dist" / "cmr-operators.zip"

# 不进交付包的目录/文件（运行产物、本地环境、权重）
EXCLUDE_PARTS = {
    "__pycache__", "weight", "runtime", "dist",
    ".venv", "venv", "env", ".pytest_cache", ".mypy_cache", ".ruff_cache",
}
EXCLUDE_SUFFIXES = {".pyc", ".pyo", ".log"}

PKG_README = """# 跨模态检索中心 · 算子服务交付包

本包即算子服务交付根目录（原仓库 operators/），构建上下文就是当前目录。

构建并启动（先按《部署说明》第二节把三个模型权重放到 ./weight 下）：

    docker compose up -d --build

验证：

    curl http://127.0.0.1:6300/healthz

- 对外仅 6300 端口；任务与索引持久化在 ./runtime，权重挂载在 ./weight。
- 配置只改 config.yaml（容器内可用环境变量 CMR_MODEL_ROOT 等覆盖路径，见 docker-compose.yml）。
- 9 个算子的接口字段见随附《算子说明书》，调用样例见《接口调用示例》，分工见《前后端对接说明》。
"""


def _excluded(rel: Path) -> bool:
    return any(part in EXCLUDE_PARTS for part in rel.parts) or rel.suffix in EXCLUDE_SUFFIXES


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.unlink()
    n = 0
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("README.md", PKG_README)
        for p in sorted(SERVICE_ROOT.rglob("*")):
            if not p.is_file():
                continue
            rel = p.relative_to(SERVICE_ROOT)
            if _excluded(rel):
                continue
            zf.write(p, rel.as_posix())
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
