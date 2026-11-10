"""407m_query_understand 查询理解算子（在线）。

对应数据流 3.2"查询有歧义→澄清浮层（最多一轮）"：
1. 模态识别：query 文本 → text；queryFile → 按魔数/扩展名识别文件模态
2. 歧义检测（初版规则，不接 LLM）：
   - 命中 configs/ambiguity.json 歧义词表 → 生成意图选项
   - 文本过短（< 4 字）且不在词表 → 提示补充描述（与后端检索前的澄清拦截阈值一致）
3. 输出 normalizedQuery 与澄清选项；needClarify=false 时调用方直接发起检索

词表是业务配置，运营可加词，不需要改代码。后续可升级 LLM 澄清（协议不变）。
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from operators.common.base import OperatorBase, OperatorError  # noqa: E402

_CONFIG_DIR = Path(__file__).resolve().parent / "configs"

EXT_MODALITY = {
    ".jpg": "image", ".jpeg": "image", ".png": "image", ".bmp": "image",
    ".webp": "image", ".gif": "image",
    ".wav": "audio", ".mp3": "audio", ".flac": "audio", ".m4a": "audio", ".ogg": "audio",
    ".mp4": "video", ".mov": "video", ".avi": "video", ".mkv": "video", ".webm": "video",
}


class Operator(OperatorBase):
    """查询理解：模态识别 + 歧义澄清选项。"""

    def __init__(self, operator_id: str, cfg: dict) -> None:
        super().__init__(operator_id, cfg)
        dict_path = _CONFIG_DIR / "ambiguity.json"
        self.ambiguity: dict[str, list[str]] = (
            json.loads(dict_path.read_text(encoding="utf-8")) if dict_path.exists() else {}
        )

    def health(self) -> dict:
        h = super().health()
        h["ambiguityTerms"] = len(self.ambiguity)
        return h

    def predict_payload(
        self, payload: dict, unique_id: str, input_file: str | None, task_out: Path
    ) -> dict:
        query = (payload.get("query") or "").strip()
        query_file = payload.get("queryFile") or input_file

        if query_file:
            p = self.resolve_input(str(query_file))
            modality = EXT_MODALITY.get(p.suffix.lower())
            if modality is None:
                raise OperatorError(f"无法识别查询文件模态: {p.name}")
            return {
                "queryModality": modality,
                "needClarify": False,
                "clarifyOptions": [],
                "normalizedQuery": None,
                "queryFile": str(query_file),  # 回显调用方原始写法（逻辑/绝对路径均可）
            }
        if not query:
            raise OperatorError("需要 query 文本或 queryFile")

        # 歧义词表命中 → 生成澄清选项（最多一轮）
        for term, meanings in self.ambiguity.items():
            if term in query:
                return {
                    "queryModality": "text",
                    "needClarify": True,
                    "clarifyOptions": [
                        {"intent": f"{term}（{m}）", "query": query.replace(term, f"{term}{m}" if len(m) <= 4 else term),
                         "hint": m}
                        for m in meanings
                    ],
                    "ambiguousTerm": term,
                    "normalizedQuery": query,
                }

        # 过短查询提示补充（规则兜底）
        if len(query) < 4:
            return {
                "queryModality": "text",
                "needClarify": True,
                "clarifyOptions": [],
                "hint": "查询过短，请补充描述（如对象、场景、动作）",
                "normalizedQuery": query,
            }

        return {
            "queryModality": "text",
            "needClarify": False,
            "clarifyOptions": [],
            "normalizedQuery": query,
        }
