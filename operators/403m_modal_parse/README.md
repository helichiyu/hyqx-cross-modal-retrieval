# 403m_modal_parse · 模态内容解析算子

## 用途

把原始内容解析成标准化条目（items.jsonl），供 404m_embed 向量化。两种模式：

- **manifest 模式**（回源接入）：目录内有 `manifest.jsonl` 时，文本直接取 content；图片/音频/视频按 `modals[].url` 临时下载到任务输出目录解析，`sourceFile` 记为上游 URL，解析后删除下载的原始文件，派生产物由 404 向量化后清理。
- **目录模式**：解析上传/指定目录内的文件，`sourceFile` 为逻辑路径。

## 接口

| 接口 | 说明 |
| --- | --- |
| `GET /403m_modal_parse/healthz` | 健康检查（含输入/输出目录可写性） |
| `POST /403m_modal_parse/uploadFile?uniqueId=...` | multipart 上传；zip 包自动解压 |
| `POST /403m_modal_parse/predict` | 解析本任务上传目录或 inputFile 指定目录 |
| `POST /403m_modal_parse/removeFile?uniqueId=...` | 清理本任务的输入/输出目录 |

### predict 请求

```json
{ "uniqueId": "task_001", "inputFile": null, "isLastOperator": false }
```

- `inputFile` 为 null：解析 `runtime/403m_modal_parse/input/{uniqueId}/` 下全部文件
- `inputFile` 为目录：解析该目录（必须在允许的根目录内）

### predict 响应 data

```json
{
  "outputFile": ".../items.jsonl",
  "lineageFile": ".../lineage.jsonl",
  "itemCount": 16,
  "byModality": {"text": 8, "image": 1, "audio": 3, "video": 6},
  "skipped": []
}
```

## 解析规则

| 模态 | 规则 |
| --- | --- |
| 文本（.txt/.md/.json/.csv） | 约 500 字一段，50 字重叠 |
| 图片（.jpg/.png/...） | 一图一条 |
| 音频（.wav/.mp3/...） | ffmpeg 切 30 秒一片，转 48kHz 单声道 wav |
| 视频（.mp4/...） | ffmpeg 按 1fps 抽帧出 jpg；音轨抽取待后续版本 |

不支持的扩展名记入 lineage（filtered=true）并在 skipped 中返回。

## 输出格式

见 [dataset/DATA_SCHEMA.md](../../dataset/DATA_SCHEMA.md) 第 3 节。`sourceFile` 与 `locator.path` 均为项目根相对**逻辑路径**（`runtime/...`、`dataset/...`），调用方按同名目录挂载即可读取媒体内容。

## 本地验证

```bash
python scripts/make_samples.py
python -m uvicorn operators.router.main:app --host 127.0.0.1 --port 6300
python scripts/e2e_m1.py
```
