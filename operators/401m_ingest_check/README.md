# 401m_ingest_check · 数据接入校验算子

## 用途

对数据集目录做接入校验：模态识别（魔数+扩展名双判）、逐文件可解码性试解码、与 metadata.json 的条数/模态构成核对。对应数据流 3.1"接入"环节。**只做校验出报告，不做通过/剔除判定**（判定归 402m_dataset_score）。

## 接口

### predict 请求

```json
{ "uniqueId": "task_001", "inputFile": "<数据集目录>" }
```

`inputFile` 为 null 时校验本算子上传目录（uploadFile 上传，zip 自动解压）。目录内可有 `metadata.json`：

```json
{ "name": "...", "version": "...", "count": 10000, "modalities": ["text","image","audio","video"], "scenes": ["..."] }
```

### predict 响应 data

| 字段 | 说明 |
| --- | --- |
| `outputFile` | report.json 路径 |
| `lineageFile` | lineage.jsonl（含 filtered=true 的失败样本与原因） |
| `failuresFile` | failures.json（失败样本清单） |
| `decodableRate` | 可解码率（0~1） |
| `modalityCoverage` | 模态覆盖率（检出的声明模态占比，无声明时按 4 模态） |
| `countRate` | 条数达标率（实际/声明，封顶 1；无声明时为 1） |
| `failureCount` | 失败文件数 |

report.json 另含 `detected`/`decodable` 分模态计数、`mismatchedExt`（扩展名与魔数不一致）、`reconcile`（元数据核对明细）。

## 判定规则

- 模态识别：魔数优先，扩展名兜底；两者都有且不一致记入 `mismatchedExt`
- 可解码性：图片 PIL verify、音视频 ffmpeg 试解码、文本 utf-8 试读
- 不支持的类型/解码失败：记 lineage（filtered=true）+ failures.json
