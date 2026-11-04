"""405m_index_build 向量索引构建算子。

把 404 的 embeddings.jsonl + 403 的 items.jsonl 合成 FAISS 索引（对应数据流 3.1"建索引"）：
- clip 通道（文本/图像/视频帧）与 clap 通道（音频）各一个索引文件
- meta.db 存 rowid ↔ 条目元数据，供检索时还原条目
- index_meta.json 记录模型版本；与当前模型版本不一致时拒绝复用，提示重建

payload 字段：
- inputFile: embeddings.jsonl（必填）
- itemsFile: items.jsonl（可选，带上可还原文本/定位信息）
- indexDir: 目标索引目录（可选，默认本任务输出目录下 index/；重复构建幂等）
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from operators.common import index_store  # noqa: E402
from operators.common.base import OperatorBase, OperatorError  # noqa: E402


def _read_jsonl(path: Path) -> list[dict]:
    return [json.loads(x) for x in path.read_text(encoding="utf-8").splitlines() if x.strip()]


class Operator(OperatorBase):
    """建索引：embeddings.jsonl (+ items.jsonl) → FAISS 索引目录。"""

    def predict_payload(
        self, payload: dict, unique_id: str, input_file: str | None, task_out: Path
    ) -> dict:
        if input_file is None:
            raise OperatorError("405 需要 inputFile 指向 embeddings.jsonl")
        embeddings = _read_jsonl(Path(input_file))
        if not embeddings:
            raise OperatorError("embeddings.jsonl is empty")

        items_by_id: dict[str, dict] = {}
        if payload.get("itemsFile"):
            items_path = self.resolve_input(payload["itemsFile"])
            items_by_id = {it["itemId"]: it for it in _read_jsonl(items_path)}

        index_dir = Path(payload["indexDir"]) if payload.get("indexDir") else task_out / "index"
        if not index_dir.is_absolute():
            # 兼容两种相对写法：runtime/...、dataset/...（项目根相对）与裸目录名（runtime 根相对）
            first = index_dir.parts[0] if index_dir.parts else ""
            base = self.project_root if first in ("runtime", "dataset") \
                else Path(self.cfg["paths"]["runtime_root"])
            index_dir = base / index_dir
        # 目标目录必须在允许的根内，防止写到任意位置
        resolved = index_dir.resolve()
        if not any(resolved == r or r in resolved.parents for r in self.allowed_roots):
            raise OperatorError(f"indexDir outside allowed roots: {index_dir}")

        # 已有索引且模型版本不同 → 明确报错，不静默混用
        model_version = self.cfg["models"]["model_version"]
        if (index_dir / "index_meta.json").exists():
            old = index_store.load_meta_info(index_dir)
            if old["modelVersion"] != model_version:
                raise OperatorError(
                    f"已有索引 modelVersion={old['modelVersion']} 与当前 {model_version} 不一致，"
                    "请换 indexDir 重建（换模型必须重建索引）"
                )

        meta = index_store.build(index_dir, embeddings, items_by_id, model_version)
        return {
            "indexDir": str(index_dir),
            "outputFile": str(index_dir / "index_meta.json"),
            "counts": meta["counts"],
            "dim": meta["dim"],
            "modelVersion": model_version,
        }
