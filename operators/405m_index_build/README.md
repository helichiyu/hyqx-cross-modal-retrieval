# 405m_index_build · 向量索引构建算子

## 用途

把 404m_embed 的 embeddings.jsonl（可带 403 的 items.jsonl 补元数据）建成 FAISS 索引，对应数据流 3.1"建索引"环节。

## 索引产物（indexDir）

| 文件 | 内容 |
| --- | --- |
| `index_clip.faiss` | 文本/图像/视频帧通道（IndexFlatIP + IDMap2，id=meta.db rowid） |
| `index_clap.faiss` | 音频通道 |
| `meta.db` | SQLite：itemId / modality / channel / sourceFile / locator |
| `index_meta.json` | modelVersion、dim、counts、builtAt |

## 接口

```json
{
  "uniqueId": "task_001",
  "inputFile": ".../embeddings.jsonl",
  "itemsFile": ".../items.jsonl",
  "indexDir": "<目标索引目录，可选>"
}
```

- `indexDir` 省略时写到本任务输出目录 `index/`；必须在允许的根目录内
- `indexDir` 相对写法两种均可：`runtime/xxx`（项目根相对逻辑路径）或裸目录名 `xxx`（runtime 根相对）；绝对路径同样接受
- 同 `indexDir` 重复构建为幂等重建（先清空再建）
- 已有索引的 `modelVersion` 与当前配置不一致时报错拒绝混用——**换模型必须重建索引**

### 响应 data

`indexDir`、`outputFile`（index_meta.json）、`counts`（分模态条数）、`dim`、`modelVersion`。

## 规模说明

起步用 `IndexFlatIP`（精确内积检索）。数据量超过约 10 万条后可切 `IndexIVFPQ`/`IndexHNSW`，检索侧接口不变。
