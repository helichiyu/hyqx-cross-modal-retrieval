"""M3 端到端验证：407 查询理解 + 408 在线检索 + 409 评测。

会重建 runtime/demo_index（meta.db 加了 text 列），然后验证在线链路。
前提：服务已启动。用法：python scripts/e2e_m3.py
"""

import json
import sys
from pathlib import Path

import httpx

BASE = "http://127.0.0.1:6300"
ROOT = Path(__file__).resolve().parents[1]
SAMPLE = ROOT / "dataset" / "sample"
INDEX_DIR = str(ROOT / "runtime" / "demo_index")
UID = "e2e_m3_001"


def check(name: str, cond: bool, detail: str = "") -> None:
    print(f"[{'PASS' if cond else 'FAIL'}] {name} {detail}")
    if not cond:
        sys.exit(1)


def call(op: str, payload: dict, timeout: int = 1800) -> dict:
    r = httpx.post(f"{BASE}/{op}/predict", json=payload, timeout=timeout)
    body = r.json()
    if body.get("code") != 0:
        print(f"  {op} 失败: {body.get('msg')}")
        sys.exit(1)
    return body["data"]


def search(payload: dict) -> dict:
    r = httpx.post(f"{BASE}/408m_retrieval/search", json=payload, timeout=300)
    return r.json()


def main() -> None:
    r = httpx.get(f"{BASE}/healthz", timeout=10).json()
    check("9 个算子已注册", len(r["data"]["operators"]) == 9, str(sorted(r["data"]["operators"])))

    # 重建索引（meta.db 新增 text 列，旧索引结构不兼容）
    d = call("403m_modal_parse", {"uniqueId": UID, "inputFile": str(SAMPLE)})
    items_file = d["outputFile"]
    d = call("404m_embed", {"uniqueId": UID, "inputFile": items_file})
    d = call("405m_index_build", {"uniqueId": UID, "inputFile": d["outputFile"],
                                  "itemsFile": items_file, "indexDir": INDEX_DIR})
    check("索引已重建(含 text 列)", d["counts"]["text"] > 0)

    # ---- 407 查询理解 ----
    d = call("407m_query_understand", {"uniqueId": UID, "query": "一只狗在沙滩上奔跑"})
    check("407 正常查询不澄清", d["needClarify"] is False and d["queryModality"] == "text")
    d = call("407m_query_understand", {"uniqueId": UID, "query": "苹果的照片"})
    check("407 歧义词出澄清选项", d["needClarify"] is True and len(d["clarifyOptions"]) == 2,
          str(d.get("clarifyOptions")))
    d = call("407m_query_understand", {"uniqueId": UID, "query": "狗"})
    check("407 过短查询提示补充", d["needClarify"] is True and "hint" in d)
    d = call("407m_query_understand", {"uniqueId": UID, "queryFile": str(SAMPLE / "dog_beach.jpg")})
    check("407 文件模态识别", d["queryModality"] == "image")

    # ---- 408 在线检索 ----
    d = search({"query": "一只狗在沙滩上奔跑", "indexDir": INDEX_DIR})
    check("408 文本检索", d["code"] == 0 and d["data"]["count"] > 0, f"count={d['data'].get('count')}")
    rst = d["data"]
    check("408 结果集编号 RST 前缀", rst["resultSetId"].startswith("RST"))
    top1 = rst["items"][0]
    check("408 top1 语义正确",
          top1["sourceFile"].endswith(("notes.txt", "dog_beach.jpg")),
          f"top1={top1['sourceFile']}({top1['modality']}) score={top1['score']}")

    d = search({"query": "一只狗在沙滩上奔跑", "indexDir": INDEX_DIR,
                "weights": {"text": 30, "image": 30, "audio": 30, "video": 30}})
    check("408 权重不合法被拒", d["code"] == 1 and "100" in d["msg"])

    d = search({"query": "租赁合同里关于宠物的条款", "indexDir": INDEX_DIR, "rerank": True})
    check("408 重排开启", d["code"] == 0 and d["data"]["strategy"]["rerank"] is True,
          f"top1={d['data']['items'][0]['sourceFile'] if d['data']['items'] else None}")

    d = search({"queryFile": str(SAMPLE / "dog_beach.jpg"), "indexDir": INDEX_DIR})
    check("408 图片查询", d["code"] == 0 and d["data"]["queryModality"] == "image",
          f"top1={d['data']['items'][0]['sourceFile'] if d['data']['items'] else None}")

    d = search({"query": "播客里讲睡眠", "indexDir": INDEX_DIR, "targetModalities": ["audio"]})
    check("408 文本查音频通道不报错", d["code"] == 0, f"count={d['data'].get('count')}")

    d = search({"query": "测试", "indexDir": str(ROOT / "runtime" / "nonexistent_index")})
    check("408 索引不存在报错", d["code"] == 1)

    # ---- 409 评测 ----
    history = ROOT / "runtime" / "409m_evaluate" / "output" / "history.jsonl"
    if history.exists():
        history.unlink()  # 清掉历史，保证「首跑无对比基线」可复现
    d = call("409m_evaluate", {"uniqueId": UID, "indexDir": INDEX_DIR,
                               "evalFile": str(SAMPLE / "eval_queries.jsonl")})
    check("409 首跑达标", d["conclusion"] == "pass", str(d["metrics"]))
    check("409 首跑无对比基线", d["comparison"] is None)

    d = call("409m_evaluate", {"uniqueId": UID, "indexDir": INDEX_DIR,
                               "evalFile": str(SAMPLE / "eval_queries.jsonl")})
    check("409 二跑自动对比上次", d["comparison"] is not None and "improved" in d["comparison"],
          str(d["comparison"]))

    d = call("409m_evaluate", {"uniqueId": UID, "indexDir": INDEX_DIR,
                               "evalFile": str(SAMPLE / "eval_queries.jsonl"),
                               "lines": {"recall": 1.01}})
    check("409 未达标分支", d["conclusion"] == "fail" and d["failedLines"] == ["recall"])

    print("\nM3 在线检索链端到端验证全部通过")


if __name__ == "__main__":
    main()
