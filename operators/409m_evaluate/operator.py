"""409m_evaluate 评测执行算子。

对应数据流 3.5"跑评测集出指标 + 对比上次出结论"：
- 逐条 query 调 408m_retrieval/search（携带评测对象策略参数），按标准答案算指标
- 指标：Recall@K、Precision@K、nDCG@K（二元相关性）
- 历史：每次评测追加写 runtime/409m_evaluate/output/history.jsonl，自动取同一 indexDir 的上次记录对比
- 结论：全部指标达线 → pass；否则 fail（转策略调整）

payload：
- indexDir（必填）、evalFile（必填，query+answers 的 jsonl）
- k（默认 10）、weights/rerank（评测对象=策略参数，可选）
- lines：验收线 {"recall": 0.8, "precision": null, "ndcg": null}（null 不卡线）
"""

from __future__ import annotations

import json
import math
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from operators.common.base import OperatorBase, OperatorError  # noqa: E402

DEFAULT_LINES = {"recall": 0.8, "precision": None, "ndcg": None}


def _ndcg(answers_hit: list[bool], n_relevant: int) -> float:
    dcg = sum(1.0 / math.log2(i + 2) for i, h in enumerate(answers_hit) if h)
    ideal = sum(1.0 / math.log2(i + 2) for i in range(min(n_relevant, len(answers_hit)) or 1))
    # IDCG 按"理想情况下命中数相同"算，命中 0 时 nDCG=0
    return dcg / ideal if ideal > 0 and any(answers_hit) else 0.0


class Operator(OperatorBase):
    """评测执行：评测集 × 策略 → 指标 + 对比结论。"""

    def predict_payload(
        self, payload: dict, unique_id: str, input_file: str | None, task_out: Path
    ) -> dict:
        index_dir = payload.get("indexDir")
        eval_file = payload.get("evalFile") or input_file
        if not index_dir or not eval_file:
            raise OperatorError("409 需要 indexDir 与 evalFile")
        eval_path = self.resolve_input(str(eval_file))
        k = int(payload.get("k", 10))
        lines = dict(DEFAULT_LINES | (payload.get("lines") or {}))

        queries = [json.loads(x) for x in eval_path.read_text(encoding="utf-8").splitlines() if x.strip()]
        if not queries:
            raise OperatorError("evalFile is empty")

        per_query = []
        recall_sum = precision_sum = ndcg_sum = 0.0
        for q in queries:
            items = self._search(q["query"], index_dir, payload, k)
            answers = q.get("answers", [])
            # 同一答案只计首次命中：同文件的多个条目不重复计入，保证 recall/ndcg ≤ 1
            covered_answers: set[str] = set()
            hits = []
            for it in items:
                hit = False
                for a in answers:
                    if a not in covered_answers and (a == it["itemId"] or it["sourceFile"].endswith(a)):
                        covered_answers.add(a)
                        hit = True
                        break
                hits.append(hit)
            n_hit = sum(hits)
            recall = len(covered_answers) / len(answers) if answers else 0.0
            precision = n_hit / k if k else 0.0
            ndcg = _ndcg(hits, len(answers))
            recall_sum += recall
            precision_sum += precision
            ndcg_sum += ndcg
            per_query.append({
                "query": q["query"], "recall": round(recall, 4),
                "precision": round(precision, 4), "ndcg": round(ndcg, 4),
                "hitCount": n_hit,
            })

        n = len(queries)
        metrics = {
            f"recall@{k}": round(recall_sum / n, 4),
            f"precision@{k}": round(precision_sum / n, 4),
            f"ndcg@{k}": round(ndcg_sum / n, 4),
        }

        # 达线判定
        failed_lines = []
        if lines.get("recall") is not None and metrics[f"recall@{k}"] < lines["recall"]:
            failed_lines.append("recall")
        if lines.get("precision") is not None and metrics[f"precision@{k}"] < lines["precision"]:
            failed_lines.append("precision")
        if lines.get("ndcg") is not None and metrics[f"ndcg@{k}"] < lines["ndcg"]:
            failed_lines.append("ndcg")

        # 对比上次（同 indexDir 的历史记录）
        history_path = self.output_root / "history.jsonl"
        previous = None
        if history_path.exists():
            for line in history_path.read_text(encoding="utf-8").splitlines():
                rec = json.loads(line)
                if rec.get("indexDir") == str(index_dir):
                    previous = rec  # 取最后一条
        comparison = None
        if previous:
            comparison = {
                f"{m}Delta": round(metrics[m] - previous["metrics"][m], 4) for m in metrics
            }
            comparison["improved"] = all(
                metrics[m] >= previous["metrics"][m] for m in metrics
            )

        record = {
            "evalId": f"EV{time.strftime('%Y%m%d%H%M%S')}",
            "indexDir": str(index_dir),
            "evalFile": str(eval_path),
            "strategy": {"weights": payload.get("weights"), "rerank": payload.get("rerank", False)},
            "metrics": metrics,
            "lines": lines,
            "failedLines": failed_lines,
            "conclusion": "pass" if not failed_lines else "fail",
            "comparison": comparison,
            "perQuery": per_query,
            "createdAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
        }
        history_path.parent.mkdir(parents=True, exist_ok=True)
        with open(history_path, "a", encoding="utf-8") as f:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")
        out = task_out / "eval_report.json"
        out.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")

        return {
            "outputFile": str(out),
            "evalId": record["evalId"],
            "metrics": metrics,
            "conclusion": record["conclusion"],
            "failedLines": failed_lines,
            "comparison": comparison,
        }

    def _search(self, query: str, index_dir: str, payload: dict, k: int) -> list[dict]:
        import httpx
        port = self.cfg["server"]["port"]
        body = {
            "query": query, "indexDir": str(index_dir), "topK": k,
            "weights": payload.get("weights"), "rerank": payload.get("rerank", False),
        }
        r = httpx.post(f"http://127.0.0.1:{port}/408m_retrieval/search", json=body, timeout=300)
        resp = r.json()
        if resp.get("code") != 0:
            raise OperatorError(f"408 检索失败: {resp.get('msg')}")
        return resp["data"]["items"]
