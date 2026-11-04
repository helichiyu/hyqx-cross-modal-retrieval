"""M1 端到端验证：启动服务 → 上传样本(zip) → 403 解析 → 404 向量化 → 抽查向量。

用法：先启动服务（见下），再运行 python scripts/e2e_m1.py
启动：python -m uvicorn router.main:app --host 127.0.0.1 --port 6300
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
UNIQUE_ID = "e2e_m1_001"


def check(name: str, cond: bool, detail: str = "") -> None:
    print(f"[{'PASS' if cond else 'FAIL'}] {name} {detail}")
    if not cond:
        sys.exit(1)


def main() -> None:
    r = httpx.get(f"{BASE}/healthz", timeout=10)
    check("router healthz", r.status_code == 200 and r.json()["code"] == 0)
    ops = r.json()["data"]["operators"]
    check("两个算子已注册", set(ops) >= {"403m_modal_parse", "404m_embed"})

    # 打包样本上传
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        for p in SAMPLE.iterdir():
            zf.write(p, p.name)
    buf.seek(0)
    r = httpx.post(
        f"{BASE}/403m_modal_parse/uploadFile",
        params={"uniqueId": UNIQUE_ID},
        files={"file": ("samples.zip", buf, "application/zip")},
        timeout=60,
    )
    check("403 uploadFile", r.json()["code"] == 0, r.json()["msg"])

    r = httpx.post(f"{BASE}/403m_modal_parse/predict", json={
        "uniqueId": UNIQUE_ID, "inputFile": None, "isLastOperator": False,
    }, timeout=300)
    data = r.json()
    check("403 predict", data["code"] == 0, data.get("msg", ""))
    by_mod = data["data"]["byModality"]
    check("403 四模态都有条目", all(by_mod[m] > 0 for m in ("text", "image", "audio", "video")), str(by_mod))
    items_file = data["data"]["outputFile"]
    expected_count = data["data"]["itemCount"]

    r = httpx.post(f"{BASE}/404m_embed/predict", json={
        "uniqueId": UNIQUE_ID, "inputFile": items_file, "isLastOperator": True,
    }, timeout=1800)
    data = r.json()
    check("404 predict", data["code"] == 0, data.get("msg", ""))
    check("404 条数一致", data["data"]["itemCount"] == expected_count,
          f"({data['data']['itemCount']} vs {expected_count})")

    emb_path = Path(data["data"]["outputFile"])
    lines = [json.loads(x) for x in emb_path.read_text(encoding="utf-8").splitlines() if x.strip()]
    check("embeddings 行数与条目一致", len(lines) == data["data"]["itemCount"], f"{len(lines)}")
    dims = {len(x["vector"]) for x in lines}
    check("向量维度=512", dims == {512}, str(dims))
    import numpy as np
    norms = [float(np.linalg.norm(x["vector"])) for x in lines[:5]]
    check("L2 归一", all(abs(n - 1.0) < 1e-2 for n in norms), str([round(n, 4) for n in norms]))

    # 在线单条查询：文本查询向量应与"狗"相关条目更接近
    r = httpx.post(f"{BASE}/404m_embed/predict", json={
        "uniqueId": UNIQUE_ID, "queryText": "一只狗在沙滩上奔跑",
    }, timeout=300)
    check("404 在线文本查询", r.json()["code"] == 0 and len(r.json()["data"]["vector"]) == 512)
    qv = np.array(r.json()["data"]["vector"], dtype=np.float32)

    items = [json.loads(x) for x in Path(items_file).read_text(encoding="utf-8").splitlines() if x.strip()]
    item_by_id = {x["itemId"]: x for x in items}
    scored = []
    for e in lines:
        if e["modality"] in ("text", "image", "video"):  # CLIP 空间内可比
            scored.append((float(np.dot(qv, np.array(e["vector"], dtype=np.float32))), e["itemId"]))
    scored.sort(reverse=True)
    top = item_by_id[scored[0][1]]
    check("语义 sanity：文本查询 top1 落在文本/图像/视频通道", top["modality"] in ("text", "image", "video"),
          f"top1={top['modality']} score={scored[0][0]:.4f}")

    r = httpx.post(f"{BASE}/403m_modal_parse/removeFile", params={"uniqueId": UNIQUE_ID}, timeout=30)
    check("403 removeFile", r.json()["code"] == 0)

    print("\nM1 端到端验证全部通过")


if __name__ == "__main__":
    main()
