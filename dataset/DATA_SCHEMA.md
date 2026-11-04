# 数据集格式说明（demo / 评测用）

> 上游"高质量数据集平台"的正式元数据 schema 尚未提供，本文件是**我方假设的工作格式**（待确认事项 Q2）。
> 拿到上游样例后需校准 401m_ingest_check / 403m_modal_parse 的字段映射。

## 1. 数据集元数据（假设）

```json
{
  "name": "数据集名称",
  "version": "v1.0",
  "count": 10000,
  "modalities": ["text", "image", "audio", "video"],
  "scenes": ["安防", "巡检"],
  "labels": [
    { "query": "一只狗在沙滩上", "answers": ["itemId 或 sourceFile"] }
  ]
}
```

- `labels` 为标注/标准答案，分层图注明"可用于评测集"，是 O6 投产校验与 E1 评测的输入。

## 2. 文件组织

```text
dataset/sample/
├── notes.txt       # 文本样本
├── dog_beach.jpg   # 图像样本
├── podcast.wav     # 音频样本（65s，解析后 3 片）
└── clip.mp4        # 视频样本（6s，解析后约 6 帧）
```

样本由 `scripts/make_samples.py` 生成，仅用于链路验证，不代表真实数据分布。

## 3. 403 产出 items.jsonl

```json
{"itemId": "...", "sourceFile": "...", "modality": "text|image|audio|video", "text": "...", "locator": {...}}
```

- `sourceFile` 与 `locator.path` 均为**项目根相对逻辑路径**（`runtime/...`、`dataset/...`）：上传/解析产物在 runtime，随数据集入库的原始文件在 dataset；下发 inputFile/queryFile 也用该写法（绝对路径兼容）
- text：`locator = {segment, charStart}`，正文在 `text` 字段
- image：`locator = {path}`
- audio：`locator = {path, startSec, endSec}`（30s 一片，48kHz 单声道 wav）
- video：`locator = {path, timeSec}`（1fps 抽帧 jpg；音轨抽取未实现，见 TODO 风险表）

## 4. 404 产出 embeddings.jsonl

```json
{"itemId": "...", "modality": "...", "vector": [512 维 float, L2 归一]}
```

- 文本/图像/视频帧：Chinese-CLIP ViT-B/16，512 维
- 音频：CLAP htsat-fused，512 维
- 两通道各自独立向量空间，融合在 408m_retrieval 按策略权重加权
