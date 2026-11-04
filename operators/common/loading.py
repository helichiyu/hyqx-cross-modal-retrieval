"""按路径加载算子模块（算子目录以数字开头，不能走常规 import）。"""

from __future__ import annotations

import importlib.util
from pathlib import Path

from operators.common.base import OperatorBase


def load_operator(operator_dir: Path, operator_id: str, cfg: dict) -> OperatorBase:
    entry = operator_dir / "operator.py"
    if not entry.exists():
        raise RuntimeError(f"operator entry not found: {entry}")
    spec = importlib.util.spec_from_file_location(f"op_{operator_id}", entry)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    op_class = getattr(module, "Operator", None)
    if op_class is None or not issubclass(op_class, OperatorBase):
        raise RuntimeError(f"{entry} must define class Operator(OperatorBase)")
    return op_class(operator_id, cfg)
