"""FAISS 索引与元数据存储：405 建、406/408 读。

布局（index_dir）：
- index_clip.faiss   文本/图像/视频帧通道（Chinese-CLIP 空间）
- index_clap.faiss   音频通道（CLAP 空间）
- meta.db            SQLite：rowid ↔ itemId/modality/sourceFile/locator
- index_meta.json    modelVersion / dim / counts / builtAt

faiss 用 IndexIDMap2 包 IndexFlatIP，id 即 meta.db 的 rowid，检索结果直接查库。
"""

from __future__ import annotations

import json
import shutil
import sqlite3
import tempfile
import time
from pathlib import Path

import numpy as np

CHANNEL_OF = {"text": "clip", "image": "clip", "video": "clip", "audio": "clap"}


class IndexError_(Exception):
    """索引不可用（缺失/版本不匹配）。"""


def _faiss_write(index, target: Path) -> None:
    """faiss 在 Windows 不支持非 ASCII 路径，经临时文件中转。"""
    import faiss

    with tempfile.NamedTemporaryFile(suffix=".faiss", delete=False) as tmp:
        tmp_path = Path(tmp.name)
    try:
        faiss.write_index(index, str(tmp_path))
        shutil.copyfile(tmp_path, target)
    finally:
        tmp_path.unlink(missing_ok=True)


def _faiss_read(source: Path):
    import faiss

    with tempfile.NamedTemporaryFile(suffix=".faiss", delete=False) as tmp:
        tmp_path = Path(tmp.name)
    try:
        shutil.copyfile(source, tmp_path)
        return faiss.read_index(str(tmp_path))
    finally:
        tmp_path.unlink(missing_ok=True)


def build(index_dir: Path, embeddings: list[dict], items_by_id: dict, model_version: str) -> dict:
    """从零构建索引（幂等：同 index_dir 重跑先清空）。"""
    import faiss

    index_dir = Path(index_dir)
    if index_dir.exists():
        for f in index_dir.glob("*"):
            f.unlink()
    index_dir.mkdir(parents=True, exist_ok=True)

    db_path = index_dir / "meta.db"
    conn = sqlite3.connect(db_path)
    conn.execute(
        "CREATE TABLE items (itemId TEXT PRIMARY KEY, modality TEXT, channel TEXT, "
        "sourceFile TEXT, locator TEXT, text TEXT DEFAULT '')"
    )
    by_channel: dict[str, list[tuple[int, np.ndarray]]] = {"clip": [], "clap": []}
    rowid = 0
    counts = {"text": 0, "image": 0, "video": 0, "audio": 0}
    for emb in embeddings:
        item = items_by_id.get(emb["itemId"], {})
        modality = emb["modality"]
        channel = CHANNEL_OF[modality]
        conn.execute(
            "INSERT INTO items VALUES (?,?,?,?,?,?)",
            (
                emb["itemId"], modality, channel,
                item.get("sourceFile", ""), json.dumps(item.get("locator", {}), ensure_ascii=False),
                item.get("text", ""),
            ),
        )
        by_channel[channel].append((rowid, np.array(emb["vector"], dtype=np.float32)))
        counts[modality] += 1
        rowid += 1
    conn.commit()
    conn.close()

    dim = 0
    for channel, rows in by_channel.items():
        if not rows:
            continue
        ids = np.array([r for r, _ in rows], dtype=np.int64)
        mat = np.stack([v for _, v in rows])
        dim = int(mat.shape[1])
        index = faiss.IndexIDMap2(faiss.IndexFlatIP(dim))
        index.add_with_ids(mat, ids)
        _faiss_write(index, index_dir / f"index_{channel}.faiss")

    meta = {
        "modelVersion": model_version,
        "dim": dim,
        "counts": counts,
        "builtAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
    }
    (index_dir / "index_meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    return meta


def load_meta_info(index_dir: Path) -> dict:
    p = Path(index_dir) / "index_meta.json"
    if not p.exists():
        raise IndexError_(f"index not found: {index_dir}")
    return json.loads(p.read_text(encoding="utf-8"))


def check_model_version(index_dir: Path, model_version: str) -> dict:
    info = load_meta_info(index_dir)
    if info["modelVersion"] != model_version:
        raise IndexError_(
            f"index modelVersion={info['modelVersion']} != current {model_version}，需重建索引"
        )
    return info


def search(index_dir: Path, vector: np.ndarray, channel: str, k: int) -> list[tuple[int, float]]:
    """返回 [(rowid, score)]，score 为内积（向量已 L2 归一即余弦相似度）。"""
    import faiss

    index_path = Path(index_dir) / f"index_{channel}.faiss"
    if not index_path.exists():
        return []
    index = _faiss_read(index_path)
    scores, ids = index.search(vector.reshape(1, -1).astype(np.float32), k)
    return [(int(i), float(s)) for i, s in zip(ids[0], scores[0]) if i >= 0]


def get_items(index_dir: Path, rowids: list[int]) -> dict[int, dict]:
    if not rowids:
        return {}
    conn = sqlite3.connect(Path(index_dir) / "meta.db")
    marks = ",".join("?" * len(rowids))
    rows = conn.execute(
        f"SELECT rowid, itemId, modality, sourceFile, locator, text FROM items WHERE rowid IN ({marks})",
        rowids,
    ).fetchall()
    conn.close()
    return {
        r[0]: {"itemId": r[1], "modality": r[2], "sourceFile": r[3],
               "locator": json.loads(r[4]), "text": r[5]}
        for r in rows
    }
