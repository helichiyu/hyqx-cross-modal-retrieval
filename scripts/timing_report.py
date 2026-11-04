"""实测各算子在样例数据集上的耗时，供说明书「性能指标」一节引用。

前提：服务已启动。输出 JSON 到 runtime/timing_report.json。
"""

import json
import time
from pathlib import Path

import httpx

BASE = "http://127.0.0.1:6300"
ROOT = Path(__file__).resolve().parents[1]
SAMPLE = ROOT / "dataset" / "sample"
INDEX_DIR = str(ROOT / "runtime" / "timing_index")
UID = "timing_001"


def timed_call(op: str, payload: dict, timeout: int = 1800) -> tuple[dict, float]:
    t0 = time.time()
    r = httpx.post(f"{BASE}/{op}/predict", json=payload, timeout=timeout)
    dt = time.time() - t0
    body = r.json()
    assert body.get("code") == 0, f"{op}: {body.get('msg')}"
    return body["data"], round(dt, 2)


def main() -> None:
    report: dict[str, float | str] = {}

    d, report["401m_ingest_check"] = timed_call("401m_ingest_check", {"uniqueId": UID, "inputFile": str(SAMPLE)})
    d, report["402m_dataset_score"] = timed_call("402m_dataset_score", {"uniqueId": UID, "inputFile": d["outputFile"]})
    d, report["403m_modal_parse"] = timed_call(
        "403m_modal_parse", {"uniqueId": UID, "inputFile": str(SAMPLE)})
    items_file = d["outputFile"]
    d, report["404m_embed_batch"] = timed_call("404m_embed", {"uniqueId": UID, "inputFile": items_file})
    emb_file = d["outputFile"]
    report["404_item_count"] = sum(1 for _ in open(emb_file, encoding="utf-8"))
    d, report["405m_index_build"] = timed_call(
        "405m_index_build", {"uniqueId": UID, "inputFile": emb_file, "itemsFile": items_file, "indexDir": INDEX_DIR})
    d, report["406m_recall_check"] = timed_call(
        "406m_recall_check", {"uniqueId": UID, "indexDir": INDEX_DIR, "evalFile": str(SAMPLE / "eval_queries.jsonl")})

    t0 = time.time()
    d = httpx.post(f"{BASE}/407m_query_understand/predict",
                   json={"uniqueId": UID, "query": "一只狗在沙滩上奔跑"}, timeout=60).json()
    report["407m_query_understand"] = round(time.time() - t0, 3)

    t0 = time.time()
    d = httpx.post(f"{BASE}/408m_retrieval/search",
                   json={"query": "一只狗在沙滩上奔跑", "indexDir": INDEX_DIR}, timeout=300).json()
    report["408m_retrieval"] = round(time.time() - t0, 3)
    report["408m_retrieval_server_ms"] = d["data"]["elapsedMs"]

    t0 = time.time()
    d = httpx.post(f"{BASE}/408m_retrieval/search",
                   json={"query": "租赁合同里关于宠物的条款", "indexDir": INDEX_DIR, "rerank": True}, timeout=600).json()
    report["408m_retrieval_rerank"] = round(time.time() - t0, 3)

    _, report["409m_evaluate"] = timed_call(
        "409m_evaluate", {"uniqueId": UID, "indexDir": INDEX_DIR, "evalFile": str(SAMPLE / "eval_queries.jsonl")})

    # 模型磁盘占用
    for name, sub in [("chinese_clip", "chinese-clip-vit-base-patch16"), ("clap", "clap-htsat-fused"),
                      ("reranker", "bge-reranker-v2-m3")]:
        p = Path("D:/models") / sub
        if p.exists():
            size_mb = sum(f.stat().st_size for f in p.rglob("*") if f.is_file()) / 1024 / 1024
            report[f"model_size_mb_{name}"] = round(size_mb)

    out = ROOT / "runtime" / "timing_report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
