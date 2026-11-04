"""406m_recall_check 投产召回率校验算子。

拿评测 query 集对索引跑 Recall@K（对应数据流 3.1"投产校验"）：
- 文本 query → 经 404 在线接口向量化（算子间走内网 HTTP，不重复加载模型）
- 在 clip 通道索引检索 Top-K，命中标准答案即记召回
- recall >= threshold → pass；否则 fail（对应"卡住 → 转人工确认"）

payload 字段：
- indexDir: 索引目录（必填）
- evalFile: 评测集 jsonl，每行 {"query": "...", "answers": ["sourceFile 片段或 itemId"]}（必填）
- k: 默认 10；threshold: 默认 0.8
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from operators.common import index_store  # noqa: E402
from operators.common.base import OperatorBase, OperatorError  # noqa: E402


class Operator(OperatorBase):
    """投产校验：评测集 × 索引 → Recall@K + 过线判定。"""

    def _embed_query(self, text: str, unique_id: str) -> np.ndarray:
        import httpx
        port = self.cfg["server"]["port"]
        r = httpx.post(
            f"http://127.0.0.1:{port}/404m_embed/predict",
            json={"uniqueId": unique_id, "queryText": text},
            timeout=300,
        )
        body = r.json()
        if body.get("code") != 0:
            raise OperatorError(f"404 向量化失败: {body.get('msg')}")
        return np.array(body["data"]["vector"], dtype=np.float32)

    def predict_payload(
        self, payload: dict, unique_id: str, input_file: str | None, task_out: Path
    ) -> dict:
        index_dir = payload.get("indexDir")
        eval_file = payload.get("evalFile") or input_file
        if not index_dir or not eval_file:
            raise OperatorError("406 需要 indexDir 与 evalFile（评测集 jsonl）")
        index_dir = self.resolve_input(str(index_dir))
        eval_path = self.resolve_input(str(eval_file))
        k = int(payload.get("k", 10))
        threshold = float(payload.get("threshold", 0.8))

        model_version = self.cfg["models"]["model_version"]
        try:
            index_store.check_model_version(index_dir, model_version)
        except index_store.IndexError_ as e:
            raise OperatorError(str(e)) from e

        queries = [json.loads(x) for x in eval_path.read_text(encoding="utf-8").splitlines() if x.strip()]
        if not queries:
            raise OperatorError("evalFile is empty")

        per_query = []
        hits = 0
        for q in queries:
            vec = self._embed_query(q["query"], unique_id)
            top = index_store.search(index_dir, vec, "clip", k)
            metas = index_store.get_items(index_dir, [rid for rid, _ in top])
            answers = q.get("answers", [])

            def is_hit(meta: dict) -> bool:
                return any(
                    a == meta["itemId"] or meta["sourceFile"].endswith(a) for a in answers
                )

            hit_items = [m | {"score": round(s, 4)} for rid, s in top if (m := metas.get(rid)) and is_hit(m)]
            hit = len(hit_items) > 0
            hits += hit
            per_query.append({
                "query": q["query"], "hit": hit,
                "hitItems": hit_items[:3],
                "top1Score": round(top[0][1], 4) if top else None,
            })

        recall = round(hits / len(queries), 4)
        passed = recall >= threshold
        result = {
            f"recall@{k}": recall,
            "queryCount": len(queries),
            "hits": hits,
            "threshold": threshold,
            "decision": "pass" if passed else "fail",
            "perQuery": per_query,
        }
        out = task_out / "recall.json"
        out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        return {
            "outputFile": str(out),
            f"recall@{k}": recall,
            "threshold": threshold,
            "decision": result["decision"],
        }
