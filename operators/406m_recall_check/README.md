# 406m_recall_check · 投产召回率校验算子

## 用途

拿评测 query 集对索引跑 Recall@K，判定是否达到投产线，对应数据流 3.1"投产校验"：过线 → 可检索；不足 → 卡住转人工确认。

## 接口

```json
{
  "uniqueId": "task_001",
  "indexDir": "<405 产出的索引目录>",
  "evalFile": "<评测集 jsonl>",
  "k": 10,
  "threshold": 0.8
}
```

评测集每行：`{"query": "...", "answers": ["sourceFile 片段或 itemId"]}`。

### 响应 data

`recall@K`、`threshold`、`decision`（pass/fail）、`outputFile`（recall.json，含逐 query 命中明细与 top1 分数，供人工确认时回看）。

## 实现说明

- 文本 query 经内网调用 404m_embed 在线接口向量化（算子间 HTTP，不重复加载模型）
- 命中判定：检索结果条目的 sourceFile 或 itemId 匹配 answers 任一值
- 索引 modelVersion 与当前模型不一致时报错（需先重建索引）
- k 与 threshold 均可由调用方按场景传入
