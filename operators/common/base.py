"""算子基类与公共能力：运行目录、路径安全、上传落盘。

协议语义沿用 SFT-demo 产线：uniqueId 标识一次任务，inputFile 为上游算子输出，
isLastOperator 标记阶段末。业务失败抛 OperatorError，由路由层转 code=1。
"""

from __future__ import annotations

import re
import shutil
import zipfile
from pathlib import Path
from typing import IO


class OperatorError(Exception):
    """业务错误：路由层转成 {"code": 1, "msg": ...}。"""


_UNIQUE_ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,128}$")


class OperatorBase:
    """所有算子的基类。子类只需实现 predict_payload()，可按需覆盖 health()。"""

    def __init__(self, operator_id: str, cfg: dict) -> None:
        self.operator_id = operator_id
        self.cfg = cfg
        self.project_root = Path(cfg["paths"]["project_root"]).resolve()
        self.runtime_root = Path(cfg["paths"]["runtime_root"]) / operator_id
        self.input_root = self.runtime_root / "input"
        self.output_root = self.runtime_root / "output"
        self.input_root.mkdir(parents=True, exist_ok=True)
        self.output_root.mkdir(parents=True, exist_ok=True)
        # inputFile 允许的来源：所有算子的运行目录 + 只读数据集目录
        roots = [cfg["paths"]["runtime_root"], cfg["paths"]["dataset_root"]]
        self.allowed_roots = [Path(r).resolve() for r in roots]

    # ---- 协议方法 ----

    def health(self) -> dict:
        return {
            "operatorId": self.operator_id,
            "status": "ok",
            "inputWritable": self.input_root.exists(),
            "outputWritable": self.output_root.exists(),
        }

    def upload(self, unique_id: str, filename: str, stream: IO[bytes]) -> dict:
        target_dir = self._task_dir(self.input_root, unique_id)
        safe_name = Path(filename).name  # 剥掉任何目录成分
        if not safe_name:
            raise OperatorError("invalid filename")
        target = target_dir / safe_name
        with open(target, "wb") as f:
            shutil.copyfileobj(stream, f)
        # zip 包自动解压，方便一次上传整个数据集
        extracted = 0
        if zipfile.is_zipfile(target):
            with zipfile.ZipFile(target) as zf:
                for info in zf.infolist():
                    # 防 zip slip
                    dest = (target_dir / info.filename).resolve()
                    if not str(dest).startswith(str(target_dir.resolve())):
                        raise OperatorError(f"unsafe entry in zip: {info.filename}")
                zf.extractall(target_dir)
                extracted = len(zf.infolist())
        return {"file": str(target), "size": target.stat().st_size, "zipEntries": extracted}

    def predict(self, payload: dict) -> dict:
        if not isinstance(payload, dict):
            raise OperatorError("payload must be a json object")
        unique_id = self._check_unique_id(payload.get("uniqueId"))
        input_file = payload.get("inputFile")
        if input_file is not None:
            input_file = str(self.resolve_input(input_file))
        task_out = self._task_dir(self.output_root, unique_id)
        return self.predict_payload(payload, unique_id, input_file, task_out)

    def remove(self, unique_id: str) -> dict:
        uid = self._check_unique_id(unique_id)
        removed = []
        for root in (self.input_root, self.output_root):
            d = root / uid
            if d.exists():
                shutil.rmtree(d)
                removed.append(str(d))
        return {"removed": removed}

    # ---- 子类实现 ----

    def predict_payload(
        self, payload: dict, unique_id: str, input_file: str | None, task_out: Path
    ) -> dict:
        raise NotImplementedError

    # ---- 工具 ----

    def relpath(self, path: Path | str) -> str:
        """绝对路径 → 项目根相对逻辑路径（runtime/...、dataset/...）。

        对外输出（items/报告/检索结果）统一走本方法，后端按同名卷挂载即可还原，
        不感知算子容器内部绝对路径。项目根之外的路径退化为绝对 posix 路径。
        """
        p = Path(path).resolve()
        try:
            return p.relative_to(self.project_root).as_posix()
        except ValueError:
            return p.as_posix()

    def resolve_input(self, input_file: str) -> Path:
        """inputFile 只能落在允许的根目录内，禁止路径穿越与 file://。

        支持两种写法：绝对路径；项目根相对逻辑路径（runtime/...、dataset/...）。
        """
        if input_file.startswith(("file://", "http://", "https://")):
            raise OperatorError("inputFile scheme not allowed")
        p = Path(input_file)
        if not p.is_absolute():
            p = self.project_root / p
        p = p.resolve()
        if not any(p == root or root in p.parents for root in self.allowed_roots):
            raise OperatorError(f"inputFile outside allowed roots: {input_file}")
        if not p.exists():
            raise OperatorError(f"inputFile not found: {input_file}")
        return p

    def _check_unique_id(self, unique_id: object) -> str:
        if not isinstance(unique_id, str) or not _UNIQUE_ID_RE.match(unique_id):
            raise OperatorError("invalid uniqueId")
        return unique_id

    def _task_dir(self, root: Path, unique_id: object) -> Path:
        uid = self._check_unique_id(unique_id)
        d = root / uid
        d.mkdir(parents=True, exist_ok=True)
        return d
