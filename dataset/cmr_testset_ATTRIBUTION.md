# cmr_testset 数据来源与许可

本目录是跨模态检索算子的**内部回归测试集**，不代表真实业务数据分布。

## 图像与中文查询

- 数据集：[Flickr30K-CN](https://huggingface.co/datasets/tomzhuo/Flickr30K-CN)
- 原始图像：Flickr30K（Young et al., 2014），图像版权归各自 Flickr 作者所有
- 中文描述：Flickr30K-CN（Li et al.）
- 用途限制：学术/研究用途；**未经确认不得对外再分发或商用**

## 音频

- 数据集：[ESC-50 Animals](https://huggingface.co/datasets/DynamicSuperb/EnvironmentalSoundClassification_ESC50-Animals)
- 原始数据：ESC-50（Karol Piczak），许可 **CC BY-NC 3.0**
- 用途限制：**仅限非商业用途**；企业交付/商用前必须替换为可商用音频源
- 本仓库中的 wav 已转码为 16kHz 单声道，仅用于减少仓库体积

## 视频

- 已于 2026-11-12（v2）整体替换为 UCF101 真实动作视频，见下方 v2 段

## 生成方式

- 图像/音频：见 [scripts/build_cmr_testset.py](../scripts/build_cmr_testset.py)，下载地址、抽样规则、转码参数均已固化，可重建
- 真实文本/真实视频（v2）：见 [scripts/fetch_real_media.py](../scripts/fetch_real_media.py)（THUCNews 抽样 + UCF101 Range 抽取，缓存于 `.tmp_fetch/` 可断点续跑）

## 真实文本与真实视频（v2，2026-11-12 重抓）

### 文本：THUCNews 新闻正文（真实新闻数据集）

- 来源：`seamew/THUCNewsText`（HuggingFace 镜像下载，test 分片，抽样 100 条）
- 原始出处：THUCNews —— 清华大学自然语言处理实验室整理的新浪网真实新闻（2005-2011），
  共 9 个类别：education/教育×12、entertainment/娱乐×11、fashion/时尚×11、finance/财经×11、politic/时政×11、society/社会×11、sport/体育×11、stock/股票×11、technology/科技×11
- 处理：按类别轮询均衡抽样；超长正文在整句边界截断（≤480 字）；仅保留简体、以中文为主的条目
- 许可：THUCNews 面向学术研究发布；本仓库仅作内部检索链路测试，不对外再分发

### 视频：UCF101 真实动作视频（真实拍摄数据集）

- 来源：`quchenyuan/UCF101-ZIP`（HuggingFace 镜像，抽取 20 条）
- 原始出处：UCF101 —— 美国中佛罗里达大学 CRCV 发布的真实 YouTube 动作视频数据集
 （Khurram Soomro et al., *UCF101: A Dataset of 101 Human Action Classes From Videos in The Wild*, 2012）
- 抽取：整包 zip 经 HTTP Range 定位抽 20 个动作类各 1 条（`scripts/fetch_ucf101_zip_range.py`）
- 处理：转码为 h264 MP4（≤12s、≤640px、去音轨）
- 许可：UCF101 供研究使用；本仓库仅作内部检索链路测试，不对外再分发

逐条对应：

- `videos/video_01.mp4` — Basketball（来源 v_Basketball_g05_c04.avi）
- `videos/video_02.mp4` — Biking（来源 v_Biking_g06_c01.avi）
- `videos/video_03.mp4` — CliffDiving（来源 v_CliffDiving_g10_c07.avi）
- `videos/video_04.mp4` — Diving（来源 v_Diving_g05_c06.avi）
- `videos/video_05.mp4` — Fencing（来源 v_Fencing_g13_c03.avi）
- `videos/video_06.mp4` — GolfSwing（来源 v_GolfSwing_g03_c02.avi）
- `videos/video_07.mp4` — HorseRace（来源 v_HorseRace_g04_c03.avi）
- `videos/video_08.mp4` — HulaHoop（来源 v_HulaHoop_g06_c03.avi）
- `videos/video_09.mp4` — IceDancing（来源 v_IceDancing_g19_c01.avi）
- `videos/video_10.mp4` — JumpingJack（来源 v_JumpingJack_g22_c04.avi）
- `videos/video_11.mp4` — PizzaTossing（来源 v_PizzaTossing_g03_c04.avi）
- `videos/video_12.mp4` — PlayingGuitar（来源 v_PlayingGuitar_g19_c07.avi）
- `videos/video_13.mp4` — PlayingPiano（来源 v_PlayingPiano_g23_c03.avi）
- `videos/video_14.mp4` — PlayingViolin（来源 v_PlayingViolin_g17_c04.avi）
- `videos/video_15.mp4` — Rafting（来源 v_Rafting_g18_c02.avi）
- `videos/video_16.mp4` — SkateBoarding（来源 v_SkateBoarding_g22_c02.avi）
- `videos/video_17.mp4` — Skiing（来源 v_Skiing_g07_c04.avi）
- `videos/video_18.mp4` — Surfing（来源 v_Surfing_g22_c02.avi）
- `videos/video_19.mp4` — TaiChi（来源 v_TaiChi_g16_c01.avi）
- `videos/video_20.mp4` — TrampolineJumping（来源 v_TrampolineJumping_g16_c03.avi）

