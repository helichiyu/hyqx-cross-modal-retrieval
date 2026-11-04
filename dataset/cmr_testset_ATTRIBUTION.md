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

- 由本测试集中的 Flickr30K-CN 图像经 ffmpeg 合成（每段 2 秒、2fps），无额外来源

## 生成方式

见 [scripts/build_cmr_testset.py](../scripts/build_cmr_testset.py)，下载地址、抽样规则、转码参数均已固化，可重建。
