"""M2 端到端验证：建库全链 401→402→403→404→405→406，并验证幂等与版本防护。

前提：服务已启动（python -m uvicorn router.main:app --port 6300），样本已生成。
用法：python scripts/e2e_m2.py
"""

import io
import json
import sys
import zipfile
from pathlib import Path

import httpx

BASE = "http://127.0.0.1:6300"
ROOT = Path(__file__).resolve().parents[1]
SAMPLE = ROOT / "dataset" / "sample"
UID = "e2e_m2_001"


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


def main() -> None:
    r = httpx.get(f"{BASE}/healthz", timeout=10).json()
    check("6 个算子已注册",
          set(r["data"]["operators"]) >= {"401m_ingest_check", "402m_dataset_score",
                                          "403m_modal_parse", "404m_embed",
                                          "405m_index_build", "406m_recall_check"})

    # 上传样本（含 metadata.json）
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        for p in SAMPLE.iterdir():
            if p.name != "eval_queries.jsonl":
                zf.write(p, p.name)
        zf.writestr("metadata.json", json.dumps({
            "name": "demo", "version": "v1", "count": 4,
            "modalities": ["text", "image", "audio", "video"], "scenes": ["demo"],
        }, ensure_ascii=False))
    buf.seek(0)
    r = httpx.post(f"{BASE}/401m_ingest_check/uploadFile", params={"uniqueId": UID},
                   files={"file": ("ds.zip", buf, "application/zip")}, timeout=60)
    check("401 uploadFile", r.json()["code"] == 0)

    upload_dir = str(ROOT / "runtime" / "401m_ingest_check" / "input" / UID)
    d = call("401m_ingest_check", {"uniqueId": UID, "inputFile": upload_dir})
    check("401 全部可解码", d["decodableRate"] == 1.0, f"rate={d['decodableRate']}")
    check("401 模态覆盖 4/4", d["modalityCoverage"] == 1.0)
    report_file = d["outputFile"]

    d = call("402m_dataset_score", {"uniqueId": UID, "inputFile": report_file})
    check("402 判定 pass", d["decision"] == "pass", f"score={d['score']}")

    # 评定挂起分支：人为压低分数线
    d = call("402m_dataset_score", {"uniqueId": UID, "inputFile": report_file,
                                    "passLine": 1.01, "suspendLine": 0.0})
    check("402 挂起分支", d["decision"] == "suspend", f"score={d['score']}")

    # 403 直接解析 401 的上传目录（产线上由后端把目录交接给下一个算子）
    d = call("403m_modal_parse", {"uniqueId": UID, "inputFile": upload_dir})
    items_file = d["outputFile"]
    check("403 有条目", d["itemCount"] > 0, str(d["byModality"]))

    d = call("404m_embed", {"uniqueId": UID, "inputFile": items_file, "isLastOperator": False})
    emb_file = d["outputFile"]

    index_dir = str(ROOT / "runtime" / "demo_index")
    d = call("405m_index_build", {"uniqueId": UID, "inputFile": emb_file,
                                  "itemsFile": items_file, "indexDir": index_dir})
    check("405 建索引", Path(d["indexDir"], "index_clip.faiss").exists()
          and Path(d["indexDir"], "index_clap.faiss").exists(), d["indexDir"])

    # 幂等：同版本重跑成功
    d = call("405m_index_build", {"uniqueId": UID, "inputFile": emb_file,
                                  "itemsFile": items_file, "indexDir": index_dir})
    check("405 幂等重建", d["modelVersion"] == "cmr-embed-v1")

    d = call("406m_recall_check", {
        "uniqueId": UID, "indexDir": index_dir,
        "evalFile": str(SAMPLE / "eval_queries.jsonl"), "k": 10, "threshold": 0.8,
    })
    check("406 召回率达标", d["decision"] == "pass", f"recall={d['recall@10']}")

    # 不过线分支
    d = call("406m_recall_check", {
        "uniqueId": UID, "indexDir": index_dir,
        "evalFile": str(SAMPLE / "eval_queries.jsonl"), "k": 10, "threshold": 1.01,
    })
    check("406 卡住分支", d["decision"] == "fail", f"recall={d['recall@10']}")

    # 路径安全：inputFile 越界必须被拒
    r = httpx.post(f"{BASE}/401m_ingest_check/predict",
                   json={"uniqueId": UID, "inputFile": "C:/Windows"}, timeout=30)
    check("路径穿越被拒", r.json()["code"] == 1)

    print("\nM2 建库链端到端验证全部通过")


if __name__ == "__main__":
    main()
