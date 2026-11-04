"""统一路由层入口：单容器单端口，按 /{operatorId}/ 分发到各算子。

启动：uvicorn router.main:app --host 0.0.0.0 --port 6300
"""

from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path

import yaml
from fastapi import FastAPI

from operators.common.loading import load_operator
from router.operator_router import build_operator_router

logger = logging.getLogger("router")

PROJECT_ROOT = Path(__file__).resolve().parent.parent


def load_config() -> dict:
    with open(PROJECT_ROOT / "config.yaml", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)
    # 路径统一解析为绝对路径；容器部署可用环境变量 CMR_MODEL_ROOT 等覆盖，免改配置文件
    for key in ("runtime_root", "dataset_root", "model_root"):
        p = Path(os.getenv(f"CMR_{key.upper()}") or cfg["paths"][key])
        cfg["paths"][key] = str(p if p.is_absolute() else PROJECT_ROOT / p)
    cfg["paths"]["project_root"] = str(PROJECT_ROOT)
    return cfg


def create_app() -> FastAPI:
    cfg = load_config()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        Path(cfg["paths"]["runtime_root"]).mkdir(parents=True, exist_ok=True)
        logger.info("registered operators: %s", sorted(app.state.operators))
        yield

    app = FastAPI(title="跨模态检索算子服务", lifespan=lifespan)
    app.state.config = cfg
    app.state.operators = {}

    for entry in cfg["operators"]:
        if not entry.get("enabled", True):
            continue
        op = load_operator(PROJECT_ROOT / "operators" / entry["id"], entry["id"], cfg)
        app.state.operators[entry["id"]] = op
        app.include_router(build_operator_router(op), prefix=f"/{entry['id']}")
        # 算子可提供四协议之外的自有路由（如 408 的 /search），挂在同一前缀下
        extra = getattr(op, "extra_router", None)
        if extra is not None:
            app.include_router(extra(), prefix=f"/{entry['id']}")

    @app.get("/healthz")
    def healthz() -> dict:
        return {
            "code": 0,
            "msg": "success",
            "data": {
                "router": "ok",
                "operators": {
                    oid: op.health() for oid, op in app.state.operators.items()
                },
            },
        }

    return app


app = create_app()
