"""认证拦截器测试（平台规范 3.4 验收清单的本地可执行部分）。

覆盖：认证关闭默认态、摘要/明文启用、401/403/放行、白名单（含 */healthz 算子探针）、
文档端点关闭、算子间内调令牌、AUTH_DISABLED 显式关闭、多调用方摘要。

进程内 TestClient 直接驱动 ASGI 应用，无需启动服务、不加载模型（懒加载）。
用法（仓库根目录）：python tests/auth_guard_test.py
"""

import hashlib
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

AUTH_VARS = ("AUTH_TOKEN_SHA256", "AUTH_TOKEN", "AUTH_DISABLED", "AUTH_WHITELIST_PATHS")

TOKEN_A = "cmr-test-token-a"
TOKEN_B = "cmr-test-token-b"
DIGEST_A = hashlib.sha256(TOKEN_A.encode()).hexdigest()
DIGEST_B = hashlib.sha256(TOKEN_B.encode()).hexdigest()

# 业务快速失败用例：无 inputFile，401 算子校验即抛 OperatorError（HTTP 200 + code=1）
PROBE_URL = "/401m_ingest_check/predict"
PROBE_PAYLOAD = {"uniqueId": "t1"}

failed = 0


def check(name: str, cond: bool, detail: str = "") -> None:
    global failed
    print(f"[{'PASS' if cond else 'FAIL'}] {name} {detail}")
    if not cond:
        failed += 1


def set_auth_env(**kwargs) -> None:
    """重置认证环境变量后按入参设置（值 None 表示不设置）。"""
    for var in AUTH_VARS:
        os.environ.pop(var, None)
    for var, value in kwargs.items():
        if value is not None:
            os.environ[var] = value


def make_client():
    """每个场景用独立 app：docs_off_kwargs/install_auth 在建 app 时定型。"""
    from fastapi.testclient import TestClient
    from operators.router.main import create_app

    return TestClient(create_app())


def business_reachable(resp, name: str) -> None:
    """通过认证到达业务层：HTTP 200 + 统一信封（业务失败 code=1 也算放行成功）。"""
    check(name, resp.status_code == 200 and resp.json().get("code") in (0, 1),
          f"status={resp.status_code}")


def main() -> None:
    saved = {var: os.environ.get(var) for var in AUTH_VARS}
    try:
        from operators.common.auth_guard import internal_headers

        # 1. 默认态（未配置令牌）：认证关闭，全部放行，文档可达
        set_auth_env()
        with make_client() as c:
            check("关闭态 /healthz 200", c.get("/healthz").status_code == 200)
            check("关闭态 算子探针 200", c.get("/401m_ingest_check/healthz").status_code == 200)
            business_reachable(c.post(PROBE_URL, json=PROBE_PAYLOAD), "关闭态 predict 放行")
            check("关闭态 /docs 可达", c.get("/docs").status_code == 200)

        # 2. 明文 AUTH_TOKEN 启用（联调形态）
        set_auth_env(AUTH_TOKEN=TOKEN_A)
        with make_client() as c:
            check("启用态 /healthz 无令牌 200", c.get("/healthz").status_code == 200)
            check("启用态 算子探针无令牌 200", c.get("/401m_ingest_check/healthz").status_code == 200)
            r = c.post(PROBE_URL, json=PROBE_PAYLOAD)
            check("启用态 缺令牌 401", r.status_code == 401 and "AUTH_MISSING" in r.json().get("msg", ""))
            r = c.post(PROBE_URL, json=PROBE_PAYLOAD, headers={"X-Token": "wrong"})
            check("启用态 错令牌 403", r.status_code == 403 and "AUTH_INVALID" in r.json().get("msg", ""))
            business_reachable(
                c.post(PROBE_URL, json=PROBE_PAYLOAD, headers={"X-Token": TOKEN_A}),
                "启用态 正确令牌放行")
            business_reachable(
                c.post(PROBE_URL, json=PROBE_PAYLOAD, headers={"token": TOKEN_A}),
                "启用态 兼容 token 头")
            business_reachable(
                c.post(PROBE_URL, json=PROBE_PAYLOAD, headers=internal_headers()),
                "启用态 算子间内调令牌放行")
            # 文档端点双保险：无令牌被拦截器挡（401），带令牌命中不到路由（404，已关闭）
            check("启用态 /docs 无令牌不可达", c.get("/docs").status_code == 401)
            check("启用态 /openapi.json 无令牌不可达", c.get("/openapi.json").status_code == 401)
            check("启用态 /docs 带令牌 404(已关闭)",
                  c.get("/docs", headers={"X-Token": TOKEN_A}).status_code == 404)
            check("启用态 /openapi.json 带令牌 404(已关闭)",
                  c.get("/openapi.json", headers={"X-Token": TOKEN_A}).status_code == 404)

        # 3. 摘要 AUTH_TOKEN_SHA256 启用（生产形态，多调用方空格分隔）
        set_auth_env(AUTH_TOKEN_SHA256=f"{DIGEST_A} {DIGEST_B}")
        with make_client() as c:
            check("摘要态 缺令牌 401", c.post(PROBE_URL, json=PROBE_PAYLOAD).status_code == 401)
            business_reachable(
                c.post(PROBE_URL, json=PROBE_PAYLOAD, headers={"X-Token": TOKEN_A}),
                "摘要态 调用方 A 放行")
            business_reachable(
                c.post(PROBE_URL, json=PROBE_PAYLOAD, headers={"X-Token": TOKEN_B}),
                "摘要态 调用方 B 放行")
            check("摘要态 错令牌 403",
                  c.post(PROBE_URL, json=PROBE_PAYLOAD, headers={"X-Token": "wrong"}).status_code == 403)
            business_reachable(
                c.post(PROBE_URL, json=PROBE_PAYLOAD, headers=internal_headers()),
                "摘要态 算子间内调放行")

        # 4. AUTH_DISABLED=1 显式关闭（即使配置了令牌）
        set_auth_env(AUTH_TOKEN=TOKEN_A, AUTH_DISABLED="1")
        with make_client() as c:
            business_reachable(c.post(PROBE_URL, json=PROBE_PAYLOAD), "DISABLED 无令牌放行")
            check("DISABLED /docs 可达", c.get("/docs").status_code == 200)

        # 5. 白名单覆盖：*/healthz 后缀规则不受覆盖影响，自定义路径放行
        set_auth_env(AUTH_TOKEN=TOKEN_A, AUTH_WHITELIST_PATHS="/health,/custom_probe")
        with make_client() as c:
            check("覆盖后 /healthz 仍放行", c.get("/healthz").status_code == 200)
            check("覆盖后 算子探针仍放行", c.get("/409m_evaluate/healthz").status_code == 200)
            r = c.get("/custom_probe")
            check("覆盖后 自定义白名单放行(404≠401)", r.status_code == 404)
            check("覆盖后 业务接口仍 401", c.post(PROBE_URL, json=PROBE_PAYLOAD).status_code == 401)
    finally:
        set_auth_env()
        for var, value in saved.items():
            if value is not None:
                os.environ[var] = value

    print(f"\n{'全部通过' if failed == 0 else f'{failed} 项失败'}")
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
