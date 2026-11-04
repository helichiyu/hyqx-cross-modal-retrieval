"""402m_dataset_score 数据集评定算子。

读 401 的 report.json，按规则加权评分并给三档判定（对应数据流 3.1"评定"）：
- score >= passLine      → pass（入库）
- suspendLine <= score < passLine → suspend（挂起，转人工裁决）
- score < suspendLine    → reject（剔除）

权重与分数线可经 payload 覆盖（weights / passLine / suspendLine），默认：
decodableRate 0.5、modalityCoverage 0.3、countRate 0.2；passLine 0.8、suspendLine 0.5。
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from operators.common.base import OperatorBase, OperatorError  # noqa: E402

DEFAULT_WEIGHTS = {"decodableRate": 0.5, "modalityCoverage": 0.3, "countRate": 0.2}
DEFAULT_PASS_LINE = 0.8
DEFAULT_SUSPEND_LINE = 0.5


class Operator(OperatorBase):
    """评定：report.json → score.json（分数 + pass/suspend/reject）。"""

    def predict_payload(
        self, payload: dict, unique_id: str, input_file: str | None, task_out: Path
    ) -> dict:
        if input_file is None:
            raise OperatorError("402 需要 inputFile 指向 401 产出的 report.json")
        report = json.loads(Path(input_file).read_text(encoding="utf-8"))

        weights = dict(DEFAULT_WEIGHTS | (payload.get("weights") or {}))
        pass_line = float(payload.get("passLine", DEFAULT_PASS_LINE))
        suspend_line = float(payload.get("suspendLine", DEFAULT_SUSPEND_LINE))
        if not suspend_line < pass_line:
            raise OperatorError("suspendLine 必须小于 passLine")

        score = round(sum(float(report.get(k, 0.0)) * w for k, w in weights.items()), 4)
        if score >= pass_line:
            decision = "pass"
        elif score >= suspend_line:
            decision = "suspend"
        else:
            decision = "reject"

        result = {
            "score": score,
            "decision": decision,
            "weights": weights,
            "passLine": pass_line,
            "suspendLine": suspend_line,
            "inputs": {k: report.get(k) for k in weights},
            "failureCount": report.get("failureCount", 0),
        }
        out = task_out / "score.json"
        out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        return {"outputFile": str(out), "score": score, "decision": decision}
