# 跨模态检索中心

当前交付范围：**算法算子包**（9 个算子，单容器单端口统一路由）+ **前后端应用**（`cmr-backend`、`cmr-web`）与**一体化部署**（`deploy/`）。

## 文档导航

- 前后端执行计划：[todos/todo_1103_2.md](todos/todo_1103_2.md)
- 一体化部署与运维：[docs/部署与运维手册.md](docs/部署与运维手册.md)、[deploy/](deploy/)
- 后端工程：[cmr-backend/](cmr-backend/)；前端工程：[cmr-web/](cmr-web/)

- 开发计划与边界：[todos/todo_1103_1.md](todos/todo_1103_1.md)
- 部署说明：[docs/部署说明.md](docs/部署说明.md)
- 接口调用示例：[docs/接口调用示例.md](docs/接口调用示例.md)
- 前后端对接说明（分工与调用指引）：[docs/前后端对接说明.md](docs/前后端对接说明.md)
- 算子说明书（docx + md）：[docs/算子说明书/](docs/算子说明书/)
- 业务依据：[【数据流图】跨模态检索中心 · 系统数据流.md](【数据流图】跨模态检索中心 · 系统数据流.md)、[分层数据流图.png](【数据流图】跨模态检索中心 · 分层数据流图.png)
- 数据格式约定：[dataset/DATA_SCHEMA.md](dataset/DATA_SCHEMA.md)
- 交互原型（仅供参考）：[原型打包/](原型打包/)
- 检索链路设计参考（DigUp 源码）：[参考/digup/](参考/digup/)

## 算子一览

| 编号 | 名称 | 类型 | 一句话职责 |
| --- | --- | --- | --- |
| 401m_ingest_check | 数据接入校验 | 批量 | 魔数+可解码双检，产出三项质量指标 |
| 402m_dataset_score | 数据集评定 | 批量 | 指标加权评分，pass/suspend/reject 三档 |
| 403m_modal_parse | 模态内容解析 | 批量 | 四模态切片为检索单元 items.jsonl |
| 404m_embed | 多模态向量化 | 批量+在线 | Chinese-CLIP/CLAP 双通道 512 维向量 |
| 405m_index_build | 向量索引构建 | 批量 | FAISS 双通道索引 + SQLite 元数据 |
| 406m_recall_check | 投产召回率校验 | 批量 | 建库后抽检回放，达标才投产 |
| 407m_query_understand | 查询理解 | 在线 | 歧义澄清、过短提示、查询模态识别 |
| 408m_retrieval | 检索服务 | 在线 | 召回+加权融合+可选精排 → 结果集 RST |
| 409m_evaluate | 评测执行 | 批量 | Recall/Precision/nDCG + 与上次对比 |

## 目录结构

```text
├── Dockerfile              # 单容器打包，对外仅 6300 端口
├── config.yaml             # 统一配置（甲方只改这里）
├── map.json                # 算子映射表
├── requirements.txt
├── router/                 # 统一路由层：/{operatorId}/healthz|uploadFile|predict|removeFile
├── operators/              # 9 个算子目录 + common（基类、协议、路径安全、索引存储）
├── dataset/                # demo 样本与数据格式说明
├── docs/                   # 部署说明、接口调用示例、9 份算子说明书
├── scripts/                # 造样本、下模型、端到端验证、耗时实测
├── tests/                  # 协议与安全测试
└── runtime/                # 运行期上传/输出（不进交付包）
```

## 本地快速验证

```bash
python -m venv .venv && .venv/Scripts/python -m pip install -r requirements.txt
.venv/Scripts/python scripts/make_samples.py
.venv/Scripts/python scripts/build_cmr_testset.py   # 可选：重建四模态回归测试集（需 pyarrow）
.venv/Scripts/python scripts/download_models.py
.venv/Scripts/python -m uvicorn router.main:app --host 127.0.0.1 --port 6300
.venv/Scripts/python scripts/e2e_m1.py
.venv/Scripts/python scripts/e2e_m2.py
.venv/Scripts/python scripts/e2e_m3.py
.venv/Scripts/python tests/protocol_test.py
```

## 部署

```bash
docker compose up -d --build
```

权重放 `./weight`（三个模型子目录，见部署说明第二节）；容器经 `CMR_MODEL_ROOT=/weight` 覆盖配置，无需改 config.yaml。算子服务仅允许后端内网访问。

## 交付包

```bash
.venv/Scripts/python scripts/make_package.py
```

生成 `dist/cmr-operators.zip`，只含 Docker 构建/运行所需内容（Dockerfile、compose、requirements、config、map.json、router/、operators/）。
