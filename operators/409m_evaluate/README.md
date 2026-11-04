# 409m_evaluate · 评测执行算子

## 用途

对已建索引跑评测集，计算 Recall@K / Precision@K / nDCG@K 三指标，对照验收线给出达标结论；同一索引的下一次评测自动与上次对比，供策略调参前后对比。对应数据流 3.2 的"评测执行"环节。

## 接口

| 接口 | 说明 |
| --- | --- |
| `GET /409m_evaluate/healthz` | 健康检查 |
| `POST /409m_evaluate/uploadFile?uniqueId=...` | multipart 上传评测集文件 |
| `POST /409m_evaluate/predict` | 执行评测，见下 |
| `POST /409m_evaluate/removeFile?uniqueId=...` | 清理本任务的输入/输出目录 |

### predict 请求

```json
{
  "uniqueId": "eval_001",
  "indexDir": "runtime/demo_index",
  "evalFile": "dataset/sample/eval_queries.jsonl",
  "k": 10,
  "lines": { "recall": 0.8, "precision": null, "ndcg": null },
  "weights": { "text": 40, "image": 20, "audio": 20, "video": 20 },
  "rerank": false
}
```

- `evalFile` 每行：`{"query": "...", "answers": ["dog_beach.jpg"]}`，answers 元素为 itemId 或来源文件名。
- `k` 与 `lines` 可省略；lines 置 null 的指标不卡线。
- `weights`/`rerank` 为评测对象策略参数，可省略（按服务端默认策略）；传入时整轮评测按该策略检索，供"生效策略 vs 候选草稿"对比。

### predict 响应 data

```json
{
  "outputFile": ".../eval_report.json",
  "metrics": { "recall@10": 1.0, "precision@10": 0.1333, "ndcg@10": 1.0 },
  "conclusion": "pass",
  "failedLines": [],
  "comparison": { "recall@10Delta": 0.0, "precision@10Delta": 0.0, "ndcg@10Delta": 0.0, "improved": true }
}
```

`comparison` 在同索引首次评测时为 null。

## 指标口径

| 指标 | 口径 |
| --- | --- |
| recall@K | 被覆盖答案数 / 答案总数（同一答案的多个条目只计一次） |
| precision@K | 命中条目数 / K |
| nDCG@K | 命中位置折损累加 / 理想折损（ideal 取 min(答案数, K)） |

历史记录追加在 `runtime/409m_evaluate/output/history.jsonl`，按 indexDir 匹配上次结果。

## 本地验证

```bash
python -m uvicorn router.main:app --host 127.0.0.1 --port 6300
python scripts/e2e_m3.py
```
