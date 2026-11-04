# 402m_dataset_score · 数据集评定算子

## 用途

读 401m_ingest_check 的 report.json，按规则加权评分并给三档判定，对应数据流 3.1"评定"环节：

| 判定 | 条件 | 去向 |
| --- | --- | --- |
| `pass` | score ≥ passLine | 入库 |
| `suspend` | suspendLine ≤ score < passLine | 卡住，转人工裁决 |
| `reject` | score < suspendLine | 剔除 |

## 接口

```json
{
  "uniqueId": "task_001",
  "inputFile": ".../report.json",
  "weights": {"decodableRate": 0.5, "modalityCoverage": 0.3, "countRate": 0.2},
  "passLine": 0.8,
  "suspendLine": 0.5
}
```

`weights` / `passLine` / `suspendLine` 均可省略用默认值；`suspendLine` 必须小于 `passLine`。

### 响应 data

`outputFile`（score.json）、`score`、`decision`。score.json 内含权重、分数线、输入比率快照，便于人工裁决时回看依据。

## 说明

评定口径是规则评分（数据质量维度），不含内容质量模型打分；后续若引入质量模型，在权重中增加维度即可，协议不变。
