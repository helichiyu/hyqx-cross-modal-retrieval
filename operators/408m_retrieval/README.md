# 408m_retrieval · 检索服务算子（在线常驻）

## 用途

在线检索主服务：查询向量化 → 双通道 ANN 召回 → z-score 加权融合（+精确词加成）→ 可选 bge-reranker 精排 → 结果集 RST。对应数据流 3.2 在线链的核心环节。

## 接口

| 接口 | 说明 |
| --- | --- |
| `GET /408m_retrieval/healthz` | 健康检查（含 rerankerLoaded、activeIndex） |
| `POST /408m_retrieval/search` | **主接口**：同步检索，前后端直连 |
| `POST /408m_retrieval/uploadFile?uniqueId=...` | multipart 上传查询文件 |
| `POST /408m_retrieval/predict` | 四协议兼容入口，逻辑同 /search，结果另存 result_set.json |
| `POST /408m_retrieval/removeFile?uniqueId=...` | 清理本任务的输入/输出目录 |

### search 请求

```json
{
  "query": "一只狗在沙滩上奔跑",
  "indexDir": "runtime/demo_index",
  "topK": 10,
  "weights": { "text": 25, "image": 25, "audio": 25, "video": 25 },
  "rerank": false,
  "wordWeight": 2.0,
  "targetModalities": ["text", "image", "audio", "video"]
}
```

- `query` 与 `queryFile` 二选一（文本查询或图片/音频/视频文件路径）。
- `indexDir` 省略时用 `config.yaml` 的 `retrieval.active_index`。
- `weights` 合计必须为 100，否则业务失败。
- 其余参数均可省略，省略时用 `retrieval` 配置默认值。

### search 响应 data

```json
{
  "resultSetId": "RST20261103153000A1B2C3",
  "query": "一只狗在沙滩上奔跑",
  "queryModality": "text",
  "count": 10,
  "items": [
    { "rank": 1, "score": 0.528178, "modality": "image",
      "itemId": "img_000002", "sourceFile": "dog_beach.jpg", "locator": null, "text": null }
  ],
  "strategy": { "weights": {}, "rerank": false, "topK": 10 },
  "modelVersion": "cmr-embed-v1",
  "elapsedMs": 727.5
}
```

## 通道与融合规则

| 查询模态 | 召回通道 |
| --- | --- |
| 文本 | clip（文/图/视频帧）+ clap（音频，文本走 CLAP 文本塔） |
| 图片/视频 | clip |
| 音频 | clap |

融合：各通道召回 topK×5，通道内 z-score 归一取非负，按四模态权重加权；精确词命中按覆盖率加 `wordWeight × coverage^1.5`（保护发票号、错误码）。精排开启时前 `rerank_top_n` 条含文本条目由 bge-reranker-v2-m3 联合打分重排。

## 本地验证

```bash
python -m uvicorn operators.router.main:app --host 127.0.0.1 --port 6300
python scripts/e2e_m3.py
```
