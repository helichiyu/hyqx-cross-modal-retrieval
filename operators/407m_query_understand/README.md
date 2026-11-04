# 407m_query_understand · 查询理解算子

## 用途

在线检索的前置环节：识别查询模态、检测歧义查询并给出澄清选项、对过短查询给出补充提示。对应数据流 3.2 在线链的"查询理解"环节。

## 接口

| 接口 | 说明 |
| --- | --- |
| `GET /407m_query_understand/healthz` | 健康检查（含歧义词典加载状态） |
| `POST /407m_query_understand/uploadFile?uniqueId=...` | multipart 上传；查询文件也可直接走 inputFile |
| `POST /407m_query_understand/predict` | 查询理解，见下 |
| `POST /407m_query_understand/removeFile?uniqueId=...` | 清理本任务的输入/输出目录 |

### predict 请求

```json
{ "uniqueId": "q_001", "query": "苹果的照片" }
```

- `query` 与 `queryFile` 二选一：前者为文本查询，后者为查询文件路径（按扩展名识别模态）。

### predict 响应 data

```json
{
  "needClarify": true,
  "clarifyOptions": [
    { "intent": "苹果水果类", "query": "苹果水果照片", "hint": "水果" }
  ],
  "queryModality": "text"
}
```

- 正常查询：`needClarify=false`，返回 `queryModality`（text/image/audio/video）。
- 歧义命中：`clarifyOptions` 每项含 `intent`（意图说明）、`query`（改写后的查询）、`hint`（提示词）。
- 过短查询（<3 字符）：`needClarify=true`，`hint` 给补充建议。
- `queryFile` 按扩展名映射模态，不触发歧义/长度检查。

## 判定规则

| 规则 | 条件 | 动作 |
| --- | --- | --- |
| 歧义消解 | 查询命中歧义词典 | 返回澄清选项，前端展示给用户选择（最多一轮） |
| 完备性检查 | 查询长度 < 3 | 返回补充提示，不进入检索 |
| 模态识别 | 输入为文件 | 按扩展名归类 text/image/audio/video |

歧义词典在 `configs/ambiguity.json`，扩充词条改配置即可，无需改代码。

## 本地验证

```bash
python -m uvicorn router.main:app --host 127.0.0.1 --port 6300
python scripts/e2e_m3.py
```
