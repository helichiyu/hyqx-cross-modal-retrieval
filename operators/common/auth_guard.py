"""统一认证拦截层（平台规范 3.4；参照数据合成产线 work/common/auth_guard.py 适配）。

供给平台上的服务直接暴露会被安全扫描判定为漏洞，因此在容器统一入口
拦截所有对外请求：请求头携带令牌，与服务端配置的令牌摘要比对——
缺失 401、不符 403、通过放行。拦截层在根应用（router/main.py）安装一次，
挂载的 9 个算子全部受保护，算子业务代码不感知。

令牌约定（"加密传输、加密存储"口径）：
- 传输：调用方在请求头 X-Token 携带令牌原文（兼容 token 头）；
  链路加密由网关/ingress TLS 承担，容器内为 HTTP
- 存储：服务端环境变量只保存令牌的 SHA-256 摘要（AUTH_TOKEN_SHA256；
  多调用方各发一个令牌时用空格/逗号分隔多个摘要），配置泄露也拿不到
  原文。联调可用明文变量 AUTH_TOKEN，摘要变量优先
- 比对用 secrets.compare_digest 防时序侧信道；不采用 MD5（已不安全）
- /health、/healthz 及任意以 /healthz 结尾的算子探针路径白名单放行
  （K8s 探针与 Docker HEALTHCHECK 不带令牌）；可用 AUTH_WHITELIST_PATHS
  覆盖精确匹配集合（空格/逗号分隔），*/healthz 后缀规则始终生效
- 认证启用时同步关闭 /docs /redoc /openapi.json（避免扫描出接口文档暴露）
- AUTH_DISABLED=1 显式关闭认证（仅限测试/本地，生产禁止）

算子间内调（406/408 → 404，409 → 408，均为容器内 127.0.0.1 HTTP 调用）：
- 进程启动时生成随机内调令牌，其摘要并入服务端接受列表；内调方经
  internal_headers() 携带原文。令牌不落盘、不外发、重启即换
- 仅适用于交付的单进程 uvicorn 形态；若改为多 worker 部署，各 worker
  令牌不一致会导致内调 401（多 worker 需改为共享令牌方案）

K8S 注入示例：
  kubectl create secret generic op-auth --from-literal=token=<令牌>
  容器环境变量 AUTH_TOKEN_SHA256 通过 secretKeyRef 引用（或直接注入摘要）
"""

from __future__ import annotations

import hashlib
import os
import secrets

TOKEN_HEADERS = ("x-token", "token")        # 令牌请求头（大小写不敏感），X-Token 优先
DIGEST_ENV = "AUTH_TOKEN_SHA256"            # 令牌 SHA-256 摘要（生产推荐，支持多个）
PLAIN_ENV = "AUTH_TOKEN"                    # 令牌明文（联调便利，摘要变量优先）
DISABLE_ENV = "AUTH_DISABLED"               # =1 显式关闭认证（仅限测试/本地）
WHITELIST_ENV = "AUTH_WHITELIST_PATHS"      # 白名单精确路径覆盖（空格/逗号分隔）
DEFAULT_WHITELIST_PATHS = {"/health", "/healthz"}
HEALTHZ_SUFFIX = "/healthz"                 # 算子探针后缀，始终放行

MISSING = (401, "AUTH_MISSING: 缺少访问令牌（请求头 X-Token）")
INVALID = (403, "AUTH_INVALID: 令牌校验失败")

# 进程内随机内调令牌：算子间 HTTP 内调专用，外部不可知
_INTERNAL_TOKEN = secrets.token_urlsafe(32)


def _digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _external_digests() -> list:
    """已配置的外部调用方令牌摘要列表；未配置返回 []。"""
    raw = os.getenv(DIGEST_ENV, "").strip()
    if not raw:
        plain = os.getenv(PLAIN_ENV, "").strip()
        raw = _digest(plain) if plain else ""
    return [item for item in raw.replace(",", " ").split() if item] if raw else []


def expected_digests() -> list:
    """服务端接受的摘要：外部配置的调用方 + 进程内内调令牌。"""
    return _external_digests() + [_digest(_INTERNAL_TOKEN)]


def auth_disabled() -> bool:
    return os.getenv(DISABLE_ENV, "").strip().lower() in {"1", "true", "yes", "on"}


def auth_enabled() -> bool:
    """认证是否生效：配置了外部令牌且未显式关闭。内调令牌不影响开关。"""
    return bool(_external_digests()) and not auth_disabled()


def token_from(headers) -> "str | None":
    """按 TOKEN_HEADERS 顺序取令牌。headers 需大小写不敏感取值
    （starlette Headers 与 http.client HTTPMessage 均满足）。"""
    for name in TOKEN_HEADERS:
        value = (headers.get(name) or "").strip()
        if value:
            return value
    return None


def verify(token: "str | None") -> tuple:
    """校验令牌，返回 (ok, status, msg)。认证未启用时直接放行。"""
    if not auth_enabled():
        return True, 0, ""
    if not token:
        return False, *MISSING
    actual = _digest(token)
    for expected in expected_digests():
        if secrets.compare_digest(actual, expected):
            return True, 0, ""
    return False, *INVALID


def whitelist_paths() -> set:
    """白名单精确路径集合（可用 AUTH_WHITELIST_PATHS 覆盖默认值）。"""
    raw = os.getenv(WHITELIST_ENV, "").strip()
    if raw:
        return {item for item in raw.replace(",", " ").split() if item}
    return set(DEFAULT_WHITELIST_PATHS)


def is_whitelisted(path: str) -> bool:
    """白名单路径判断（忽略尾部斜杠；query 由调用方先行剥离）。

    除精确匹配外，任意以 /healthz 结尾的路径（各算子探针）始终放行。
    """
    normalized = path.rstrip("/") or "/"
    if normalized in whitelist_paths():
        return True
    return normalized.endswith(HEALTHZ_SUFFIX)


def internal_headers() -> dict:
    """算子间内调请求头：携带进程内随机令牌，认证开启时内调仍可通过。"""
    return {"X-Token": _INTERNAL_TOKEN}


def docs_off_kwargs() -> dict:
    """认证启用时返回关闭文档端点的 FastAPI 构造参数。"""
    if auth_enabled():
        return {"docs_url": None, "redoc_url": None, "openapi_url": None}
    return {}


def install_auth(app) -> bool:
    """给 FastAPI 应用安装认证拦截中间件；认证未启用时跳过并告警。"""
    if auth_disabled():
        print(f"[WARN] {DISABLE_ENV} 已设置，认证拦截被显式关闭（仅限测试/本地，生产禁止）")
        return False
    if not _external_digests():
        print(f"[WARN] 未设置 {DIGEST_ENV}/{PLAIN_ENV}，认证拦截未启用"
              f"（生产部署必须注入令牌摘要，见平台规范 3.4）")
        return False

    from fastapi.responses import JSONResponse  # fastapi 为可选依赖，延迟导入

    @app.middleware("http")
    async def _auth_interceptor(request, call_next):
        if is_whitelisted(request.url.path):
            return await call_next(request)
        ok, status, msg = verify(token_from(request.headers))
        if not ok:
            return JSONResponse(content={"code": 1, "msg": msg}, status_code=status)
        return await call_next(request)

    print(f"[AUTH] 认证拦截已启用（白名单: {', '.join(sorted(whitelist_paths()))} "
          f"+ *{HEALTHZ_SUFFIX}；文档端点已关闭）")
    return True
