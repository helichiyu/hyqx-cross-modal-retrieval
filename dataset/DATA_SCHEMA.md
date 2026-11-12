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

## 5. 四模态回归测试集 `cmr_testset/`

`sample/` 只用于链路冒烟；需要真实语义和指标回归时使用 `dataset/cmr_testset/`：
100 张原图 + 30 张比例变体 + 50 条音频 + 20 条混合音频 + 20 段真实视频 + 100 篇真实新闻文本（可入库）。

| 路径 | 内容 |
| --- | --- |
| `cmr_testset/images/` | 100 张 jpg（Flickr30K-CN）+ 30 张方形/竖屏/横屏比例变体 |
| `cmr_testset/audio/` | 50 条 5s、16kHz 单声道 wav（ESC-50 动物类）+ 20 条混合音频 |
| `cmr_testset/videos/` | 20 段真实动作视频（UCF101，20 个动作类各 1 条，h264 mp4） |
| `cmr_testset/text/` | 100 篇真实新闻正文（THUCNews，9 个类别，≤480 字简体） |
| `cmr_testset/metadata.json` | 401 声明的 count/modalities（count 为实际文件数） |
| `cmr_testset_eval/eval_image.jsonl` | 100 条中文查询 → 图像 |
| `cmr_testset_eval/eval_audio.jsonl` | 50 条英文查询 → 音频 |
| `cmr_testset_eval/eval_video.jsonl` | 10 条中文查询 → 真实视频 |
| `cmr_testset_eval/eval_text.jsonl` | 10 条中文查询 → 新闻文本 |
| `cmr_testset_eval/eval_all.jsonl` | 合并 170 条，供 409 使用 |

- 评测集放在数据集目录**之外**，避免 403 把 `*.jsonl` 当文本解析。
- 完整版（1000 图 / 200 音频）与本地重建：
  ```bash
  python -m pip install pyarrow
  python scripts/build_cmr_testset.py --images 1000 --audio 200
  ```
- 已有数据目录做增量补素材（视频运镜、比例变体、混合音频、多篇文本）：
  ```bash
  .venv/Scripts/python scripts/enrich_cmr_testset.py --dataset dataset/cmr_testset
  ```
- 真实文本/真实视频（v2，2026-11-12）拉取与重跑：
  ```bash
  .venv/Scripts/python scripts/fetch_real_media.py --only text   # THUCNews → text/txt_*.txt
  .venv/Scripts/python scripts/fetch_ucf101_zip_range.py         # UCF101 整包 zip Range 抽取
  .venv/Scripts/python scripts/fetch_real_media.py --only video  # 转码 → videos/video_*.mp4
  ```
- 来源与许可见 [cmr_testset_ATTRIBUTION.md](cmr_testset_ATTRIBUTION.md)；仅限内部测试，对外交付前需确认授权。
