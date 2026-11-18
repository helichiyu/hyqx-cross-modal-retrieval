"""生成交付包：只含算子服务的 Docker 构建/运行内容。

交付根目录为 operators/（含 Dockerfile、docker-compose.yml、config.yaml、map.json、
requirements.txt、router/、common/ 与 9 个算子目录），压缩包内保持同样的顶层结构。

产物 dist/op-cross-modal-search.zip；不含 weight/、runtime/、dataset/、docs/、scripts/、tests/
（模型权重、运行产物、文档与测试数据另行交付）。

用法：python scripts/make_package.py
"""

import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE_ROOT = ROOT / "operators"
OUT = ROOT / "dist" / "op-cross-modal-search.zip"

# 不进交付包的目录/文件（运行产物、本地环境、权重）
EXCLUDE_PARTS = {
    "__pycache__", "weight", "runtime", "dist",
    ".venv", "venv", "env", ".pytest_cache", ".mypy_cache", ".ruff_cache",
}
EXCLUDE_SUFFIXES = {".pyc", ".pyo", ".log", ".env"}
EXCLUDE_NAMES = {".env"}

PKG_README = """# 跨模态检索中心 · 算子服务交付包

本包即算子服务交付根目录（原仓库 operators/），构建上下文就是当前目录。

构建并启动（先按《部署说明》第二节把三个模型权重放到 ./weight 下）：

    docker compose up -d --build

验证（默认宿主机端口 17021，容器内固定 6300）：

    curl http://127.0.0.1:17021/healthz

- 服务名/容器名/镜像名均为 op-cross-modal-search，compose 项目名同名；镜像 tag 1.0.0。
- 宿主机端口用环境变量控制：CMR_OPERATOR_PORT=17021（不设置则默认 17021），只改这一处，无需改文件。
- 任务与索引持久化在 ./runtime（可用 CMR_HOST_RUNTIME_DIR 指向宿主机其它目录），权重挂载在 ./weight。
- 数据集目录默认 ./dataset，只读挂载到 /app/dataset（可用 CMR_HOST_DATASET_DIR 指向与后端共享的目录）；
  后端传入 dataset/... 逻辑路径时必须挂载同一份数据，否则 401/406 找不到文件。
- 配置只改 config.yaml（容器内可用环境变量 CMR_MODEL_ROOT 等覆盖路径，见 docker-compose.yml）。
- 9 个算子的接口字段见随附《算子说明书》，调用样例见《接口调用示例》，分工见《前后端对接说明》。

## 入站认证（平台规范 3.4）

- 启用：环境注入 `CMR_AUTH_TOKEN_SHA256=<调用方令牌的 SHA-256 摘要>`（compose 已转注为
  容器变量 `AUTH_TOKEN_SHA256`，多个摘要用空格/逗号分隔），改环境变量重启即生效，
  不需要改代码、不需要重建镜像。未注入时认证关闭并打印 [WARN]（仅限内网测试环境）。
- 协议：调用方请求头携带 `X-Token: <原始令牌>`（兼容 `token` 头）；
  服务端只存 SHA-256 摘要，摘要不泄原始令牌；缺令牌 401、令牌不符 403。
- 白名单：`/health`、`/healthz` 及任意以 `/healthz` 结尾的算子探针路径放行，
  探针/健康检查无需令牌；健康检查一律用 `/healthz`，不要用 `/openapi.json`。
- 认证启用后 `/docs`、`/redoc`、`/openapi.json` 自动关闭。
- 算子间内调（406/408→404、409→408，容器内回环 HTTP）使用进程内随机令牌，自动放行，无需配置。
- 联调可用明文变量 `AUTH_TOKEN`（摘要变量优先）；`AUTH_DISABLED=1` 显式关闭认证（仅限测试/本地）。
- 调用方（后端）拿到的是原始令牌，需在请求头携带；服务端只配摘要，请妥善保管原始令牌，
  丢失无法反推，只能轮换新令牌。
"""


def _excluded(rel: Path) -> bool:
    return (
        any(part in EXCLUDE_PARTS for part in rel.parts)
        or rel.suffix in EXCLUDE_SUFFIXES
        or rel.name in EXCLUDE_NAMES
        or rel.name.startswith(".env.")
    )


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
