"""算子四协议路由：/healthz /uploadFile /predict /removeFile。

每个算子实现 operators.common.base.OperatorBase，这里把它包装成 FastAPI 路由。
统一信封：{"code": 0, "msg": "success", "data": {...}}；业务失败 code=1。
"""

from __future__ import annotations

import logging
import time
import uuid

from fastapi import APIRouter, Query, Request, UploadFile
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

from operators.common.base import OperatorBase, OperatorError

logger = logging.getLogger("router.operator")


def _ok(data: dict | None = None) -> dict:
    return {"code": 0, "msg": "success", "data": data or {}}


def _fail(msg: str, status: int = 200) -> JSONResponse:
    # 业务失败沿用统一信封 code=1（HTTP 200）；协议/技术错误用 HTTP 状态码
    return JSONResponse(status_code=status, content={"code": 1, "msg": msg, "data": {}})


def build_operator_router(op: OperatorBase) -> APIRouter:
    router = APIRouter()

    @router.get("/healthz")
    def healthz() -> dict:
        return _ok(op.health())

    @router.post("/uploadFile")
    async def upload_file(uniqueId: str = Query(...), file: UploadFile = None):
        if file is None:
            return _fail("missing file", status=400)
        try:
            return _ok(await run_in_threadpool(op.upload, uniqueId, file.filename or "unnamed", file.file))
        except OperatorError as e:
            return _fail(str(e))

    @router.post("/predict")
    async def predict(request: Request):
        request_id = uuid.uuid4().hex[:12]
        started = time.time()
        try:
            payload = await request.json()
        except Exception:
            return _fail("invalid json body", status=400)
        try:
            # 算子是同步实现且可能内调其它算子的 HTTP 接口，必须在线程池跑，避免阻塞事件循环死锁
            result = await run_in_threadpool(op.predict, payload)
        except OperatorError as e:
            logger.warning(
                "predict failed requestId=%s operator=%s uniqueId=%s: %s",
                request_id, op.operator_id, payload.get("uniqueId"), e,
            )
            return _fail(str(e))
        logger.info(
            "predict done requestId=%s operator=%s uniqueId=%s elapsed=%.2fs",
            request_id, op.operator_id, payload.get("uniqueId"), time.time() - started,
        )
        return _ok(result)

    @router.post("/removeFile")
    def remove_file(uniqueId: str = Query(...)):
        try:
            return _ok(op.remove(uniqueId))
        except OperatorError as e:
            return _fail(str(e))

    return router
