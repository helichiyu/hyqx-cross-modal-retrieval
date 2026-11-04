"""协议与安全测试：四协议、路由、路径安全、参数校验（按路由层改造提示词第十一节裁剪）。

前提：服务已启动。用法：python tests/protocol_test.py
"""

import io
import sys

import httpx

BASE = "http://127.0.0.1:6300"
OPERATORS = ["401m_ingest_check", "402m_dataset_score", "403m_modal_parse", "404m_embed",
             "405m_index_build", "406m_recall_check", "407m_query_understand",
             "408m_retrieval", "409m_evaluate"]

failed = 0


def check(name: str, cond: bool, detail: str = "") -> None:
    global failed
    print(f"[{'PASS' if cond else 'FAIL'}] {name} {detail}")
    if not cond:
        failed += 1


def main() -> None:
    # 1. 路由层与每个算子的健康检查
    r = httpx.get(f"{BASE}/healthz", timeout=10).json()
    check("统一 healthz", r["code"] == 0 and r["data"]["router"] == "ok")
    for op in OPERATORS:
        r = httpx.get(f"{BASE}/{op}/healthz", timeout=10).json()
        check(f"{op} healthz", r["code"] == 0 and r["data"]["status"] in ("ok", "degraded"))

    # 2. 无效 operatorId
    r = httpx.get(f"{BASE}/999m_nonexist/healthz", timeout=10)
    check("无效 operatorId 返回 404", r.status_code == 404)

    # 3. uploadFile 缺文件
    r = httpx.post(f"{BASE}/403m_modal_parse/uploadFile", params={"uniqueId": "pt_001"}, timeout=10)
    check("uploadFile 缺文件返回 400", r.status_code == 400)

    # 4. 非法 uniqueId（注入尝试）
    for bad in ("../etc", "a/b", "a..b", "", "a" * 200):
        r = httpx.post(f"{BASE}/403m_modal_parse/predict",
                       json={"uniqueId": bad, "inputFile": None}, timeout=10)
        check(f"非法 uniqueId 被拒: {bad!r:.20}", r.json().get("code") == 1)

    # 5. inputFile 路径穿越与 file://
    for bad in ("C:/Windows", "../../etc", "file:///C:/Windows/win.ini", "http://x/y"):
        r = httpx.post(f"{BASE}/403m_modal_parse/predict",
                       json={"uniqueId": "pt_001", "inputFile": bad}, timeout=10)
        check(f"inputFile 越界被拒: {bad}", r.json().get("code") == 1)

    # 6. 非法 JSON body
    r = httpx.post(f"{BASE}/403m_modal_parse/predict", content=b"not json",
                   headers={"Content-Type": "application/json"}, timeout=10)
    check("非法 JSON 返回 400", r.status_code == 400)

    # 7. 上传 zip slip 防护
    import zipfile
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("../evil.txt", "evil")
    buf.seek(0)
    r = httpx.post(f"{BASE}/403m_modal_parse/uploadFile", params={"uniqueId": "pt_zipslip"},
                   files={"file": ("evil.zip", buf, "application/zip")}, timeout=30)
    check("zip slip 被拒", r.json().get("code") == 1)

    # 8. removeFile 幂等（不存在的 uniqueId 不报错）
    r = httpx.post(f"{BASE}/403m_modal_parse/removeFile", params={"uniqueId": "pt_nonexist"}, timeout=10)
    check("removeFile 幂等", r.json().get("code") == 0)

    # 9. removeFile 只能删本任务目录
    httpx.post(f"{BASE}/403m_modal_parse/uploadFile", params={"uniqueId": "pt_keep"},
               files={"file": ("a.txt", io.BytesIO("你好".encode()), "text/plain")}, timeout=30)
    httpx.post(f"{BASE}/403m_modal_parse/removeFile", params={"uniqueId": "pt_other"}, timeout=10)
    r = httpx.post(f"{BASE}/403m_modal_parse/removeFile", params={"uniqueId": "pt_keep"}, timeout=10)
    check("removeFile 按 uniqueId 隔离", r.json()["code"] == 0 and len(r.json()["data"]["removed"]) > 0)

    # 10. 检索服务协议：缺 query
    r = httpx.post(f"{BASE}/408m_retrieval/search", json={}, timeout=30)
    check("search 缺 query 返回业务失败", r.json().get("code") == 1)

    print(f"\n协议测试完成: {'全部通过' if failed == 0 else f'{failed} 项失败'}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
