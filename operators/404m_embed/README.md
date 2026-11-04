# 404m_embed · 多模态向量化算子

## 用途

把 403m_modal_parse 产出的条目编码成 512 维向量（L2 归一），供 405m_index_build 建索引；同时提供在线单条查询向量化，供 408m_retrieval 复用同一模型实例。对应数据流"统一向量化（进同一空间）"环节。

## 模型

| 通道 | 模型 | 维度 | 权重目录（model_root 下） |
| --- | --- | --- | --- |
| 文本 | Chinese-CLIP ViT-B/16 | 512 | chinese-clip-vit-base-patch16 |
| 图像 | Chinese-CLIP 图像塔 | 512 | 同上 |
| 视频帧 | Chinese-CLIP 图像塔 | 512 | 同上 |
| 音频 | CLAP htsat-fused | 512 | clap-htsat-fused |

文本/图像/视频帧同一空间（Chinese-CLIP），音频独立空间（CLAP）；跨通道融合由 408m_retrieval 按策略权重加权。模型版本号（config.models.model_version）写入输出，索引头据此判断失效重建。

## 接口

### 批处理（建库链）

```json
{ "uniqueId": "task_001", "inputFile": ".../items.jsonl", "isLastOperator": true }
```

响应 data：`outputFile`（embeddings.jsonl）、`itemCount`、`dim`、`modelVersion`。

items.jsonl 中的 `locator.path` 既接受项目根相对逻辑路径（`runtime/...`、`dataset/...`，403 现行输出），也接受历史版本的绝对路径。

### 在线单条查询

```json
{ "uniqueId": "task_001", "queryText": "一只狗在沙滩上" }
```

或 `queryImagePath` / `queryAudioPath`（路径须在允许的根目录内）。响应 data：`modality`、`dim`、`vector`、`modelVersion`。

### 健康检查

`GET /404m_embed/healthz` 返回模型加载状态与权重目录是否存在；权重缺失时 `status=degraded`。

## 资源要求

- CPU 可跑通；单卡 GPU（≥8GB）可显著提速
- 内存 ≥ 8GB（双模型常驻约 4GB）

## 评价指标

向量质量由 406m_recall_check / 409m_evaluate 的 Recall@10 / Precision@10 / nDCG@10 体现，本算子不单独出指标。
