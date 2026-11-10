#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""本地模拟上游数据源（P4「接入」联调用）。

实现 docs 里约定的三只读接口，用于在没有真实上游时验证
「测试连接 → 同步发现 → 接入拉内容 → 401~406 建库」整条链路：

    GET /datasets                          数据集列表
    GET /datasets/{dataset_id}             数据集详情
    GET /datasets/{dataset_id}/items?page=1&size=20   条目分页
    GET /media/{file}                      模态文件（图片/音频/视频）

用法：
    .venv/Scripts/python.exe scripts/mock_datasource_upstream.py --port 7001 --media dataset/v5_sample

默认数据集：ds_alpha（3 条，含文本与图片）、ds_blacklist_demo（1 条，用于黑名单联调）。
"""

from __future__ import annotations

import argparse
import json
import mimetypes
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse


def build_datasets(base: str) -> list[dict]:
    return [
        {
            "dataset_id": "ds_alpha",
            "name": "alpha-sample",
            "version": "v1",
            "item_count": 3,
            "modal_types": ["text", "image"],
        },
        {
            "dataset_id": "ds_blacklist_demo",
            "name": "blacklist-demo",
            "version": "v1",
            "item_count": 1,
            "modal_types": ["text"],
        },
    ]


def build_items(dataset_id: str, base: str) -> list[dict]:
    if dataset_id == "ds_alpha":
        return [
            {
                "item_id": "it_001",
                "modals": [{"type": "text", "content": "海边的小狗在沙滩上奔跑"}],
            },
            {
                "item_id": "it_002",
                "modals": [{"type": "image", "url": f"{base}/media/dog_beach.jpg"}],
            },
            {
                "item_id": "it_003",
                "modals": [{"type": "text", "content": "海边的小狗在海浪里追逐玩具"}],
            },
        ]
    if dataset_id == "ds_blacklist_demo":
        return [
            {
                "item_id": "it_bad_001",
                "modals": [{"type": "text", "content": "该数据集用于验证黑名单跳过"}],
            },
        ]
    return []


class Handler(BaseHTTPRequestHandler):
    media_root: Path = Path("dataset/v5_sample")
    base: str = "http://127.0.0.1:7001"

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/")
        if path == "":
            # 数据源「测试连接」会直接 ping endpoint 根地址
            self.send_json({
                "service": "mock-datasource-upstream",
                "endpoints": ["/datasets", "/datasets/{dataset_id}", "/datasets/{dataset_id}/items"],
            })
            return
        if path == "/datasets":
            self.send_json({"datasets": build_datasets(self.base)})
            return
        if path.startswith("/datasets/"):
            parts = path.split("/")
            # /datasets/{id}
            if len(parts) == 3:
                dataset_id = parts[2]
                for ds in build_datasets(self.base):
                    if ds["dataset_id"] == dataset_id:
                        self.send_json(ds)
                        return
                self.send_error(404, "dataset not found")
                return
            # /datasets/{id}/items
            if len(parts) == 4 and parts[3] == "items":
                dataset_id = parts[2]
                query = parse_qs(parsed.query)
                page = int(query.get("page", ["1"])[0])
                size = int(query.get("size", ["20"])[0])
                items = build_items(dataset_id, self.base)
                start = (page - 1) * size
                self.send_json({"items": items[start:start + size], "total": len(items)})
                return
        if path.startswith("/media/"):
            name = Path(path).name
            target = (self.media_root / name).resolve()
            if not target.exists() or not target.is_file():
                self.send_error(404, "media not found")
                return
            data = target.read_bytes()
            content_type = mimetypes.guess_type(name)[0] or "application/octet-stream"
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        self.send_error(404, "not found")

    def send_json(self, payload: dict) -> None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt: str, *args) -> None:  # 静默访问日志
        pass


def main() -> None:
    parser = argparse.ArgumentParser(description="模拟上游数据源接口")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=7001)
    parser.add_argument("--media", default="dataset/v5_sample", help="模态文件目录")
    args = parser.parse_args()

    Handler.media_root = Path(args.media)
    Handler.base = f"http://{args.host}:{args.port}"
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"mock upstream listening on {Handler.base}, media={Handler.media_root}")
    server.serve_forever()


if __name__ == "__main__":
    main()
