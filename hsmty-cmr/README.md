# hsmty-module-cmr-server（跨模态检索中心）

按 HSMTY 平台基线（`client → core → start`）搭建的跨模态检索服务，业务实现已从
仓库根目录的 `cmr-backend` / `cmr-web` 全量迁移到本工程：数据源/库管理、检索（407 查询理解
+ 408 召回）、结果回看与摘要、搜索行为、策略版本、任务流水、效果评测、运营反馈均已有
真实接口支撑。旧工程保留作功能基线，不再新增功能。

## 1. 目录

```
hsmty-cmr/
├── hsmty-module-cmr-client/   对外契约：Feign、DTO、VO
├── hsmty-module-cmr-core/     业务实现：Controller / Service / Mapper / PO / Liquibase
├── hsmty-module-cmr-start/    启动类、全局配置、application.yml、集成测试
├── web/                       qiankun 子应用 cross-modal-search
├── deploy/                    docker compose 编排与 .env 样例
├── settings.example.xml       Maven 设置模板（复制为 settings.xml 并填凭据，settings.xml 不入库）
└── Dockerfile                 后端镜像（Jenkins 构建后打包）
```

后端根包 `com.hsmty.cmr`，启动类为 `com.hsmty.RunApplication`；业务接口统一为
`/hs-api/cmr/**`；前端部署路径 `/sdom-hsmty/`。

## 2. 获取平台依赖

平台公共能力（父 POM、公共库）来自公司内网：

- 模板仓库：`http://192.168.1.110:13000/hsmty/hsmty-module-cms-server`
- Maven 仓库：`http://192.168.1.110:13001/repository/maven-public/`
- 父工程：`com.hsmty:hsmty-module-starter:2.0.1`（本工程 `revision` 必须与父工程一致）

首次使用复制脱敏模板并填入自己的 Nexus 账号（`settings.xml` 已被 git 忽略）：

```
cp settings.example.xml settings.xml
```

之后 CLI 构建统一使用 `mvn -s settings.xml`。

## 3. 构建与运行

```
mvn -s settings.xml -DskipTests clean package
java -jar hsmty-module-cmr-start/target/hsmty-module-cmr-start.jar
```

- 端口 `8080`，上下文 `/hs-api`
- 接口文档 `/hs-api/swagger-ui.html`，OpenAPI `/hs-api/v3/api-docs`
- 业务接口统一为 `/hs-api/cmr/**`
- 契约模块 `skipTests=true`；start 的 surefire 默认跳过测试，执行测试需显式
  `-DskipTests=false`

本地执行数据库变更：

```
java -jar hsmty-module-cmr-start/target/hsmty-module-cmr-start.jar --spring.liquibase.enabled=true
```

本机联调使用 `local` profile（本机 MySQL + 单机 Redis + 6300 算子，端口 `18080`，
自动开启 Liquibase）：

```
LOCAL_DB_PASSWORD=... java -jar hsmty-module-cmr-start/target/hsmty-module-cmr-start.jar --spring.profiles.active=local
```

`local` 会放行全部路径并由 `LocalDevSecurityConfiguration` 注入固定身份，仅限本机使用，
不得用于共享/生产环境。

本机 MySQL / Redis / 算子 / 后端 / 前端的启动顺序、端口、停止方式与数据库增删改查，
见 [docs/本机联调手册.md](docs/本机联调手册.md)。

## 4. 接口与权限

所有接口返回平台 `ApiResult`（成功 `code=0000`，业务失败码带前缀，如 `C1003`），
分页返回 `ApiPageResult`（`current/limit/total/totalPage/rows`）。Controller 一律
`@PreAuthorize` + `hasAnyAuthority`，权限码与 `Permissions` 常量、菜单数据三处一致。

| 业务域 | 基础路径（`/hs-api/cmr` 之后） | 主要能力 |
| --- | --- | --- |
| 鉴权/字典 | `auth`、`dict`、`recommend-words` | 当前用户、字典项、推荐词维护 |
| 文件 | `file`、`files` | 上传、按路径下载、文件对象分页/详情/批量查 |
| 库管理 | `datasets`、`datasources`、`datasource-blacklist`、`indexes` | 数据集审核/生产化/重建、数据源对接与测试、同步、上游数据集拉取与接入（401→406）、黑名单、索引激活 |
| 检索 | `search`、`search-history` | 407 查询理解、408 检索落库、结果回看/重跑/点击埋点、收藏 |
| 行为/摘要 | `search-behaviors`、`search-summary` | 行为流水、屏蔽条目与解除、任务摘要与封存、赞踩 |
| 策略 | `strategies` | 草稿保存、发布、下线/上线、版本列表/回滚、跨策略与跨版本对比、一致性检查 |
| 任务 | `tasks` | 任务分页/详情、失败任务处置状态 |
| 评测 | `evaluations`、`eval-sets`、`eval-runs` | 新建评测、回放、难例样本、检查评测数据（`GET /evaluations/check-data` 4 项只读）、评测集标注与运行 |
| 运营 | `feedbacks` | 差异/零结果反馈、重复任务合并、处置统计 |

### 检索算子

算子保持独立容器（决策 D-006），后端通过 Feign 调用，地址只来自配置：

| 配置 | 本地/测试默认 | 生产 |
| --- | --- | --- |
| `cmr.operator.base-url` | `http://127.0.0.1:6300` | 环境变量 `CMR_OPERATOR_BASE_URL`（compose 内 `http://cmr-operators:6300`） |
| 在线链超时 | `connect 2s / read 60s` | 同左，按需覆盖 |
| 批量链超时 | 预留 `1800000ms` | 同左 |

已接入 407 查询理解（`/search/understand`）与 408 检索（`/search`）。408 内部完成
阈值过滤、TopK 截断与分数排序，后端不再二次过滤/排序；`score` 为融合分、`alignScore`
为原始余弦相似度，前端展示优先使用 `alignScore`。算子返回自有信封（`{code:0,msg,data}`），
因此 `CmrOperatorFeignClient` 使用专用 `CmrOperatorFeignConfig`，不复用平台的
`FeignResultDecoder`；原因见 `docs/对齐偏差登记.md`。

## 5. 数据库

- 库：`sd_data_engineering`（与其它模块共用，按表前缀 `cmr_` 隔离）
- 表：`cmr_file_object`、`cmr_dataset`、`cmr_datasource`、`cmr_datasource_blacklist`、
  `cmr_index`、`cmr_search_history`、`cmr_result_item`、`cmr_click_event`、`cmr_item_vote`、
  `cmr_favorite`、`cmr_blocked_item`、`cmr_task`、`cmr_task_log`、`cmr_strategy`、
  `cmr_strategy_version`、`cmr_feedback`、`cmr_recommend_word`、`cmr_eval_set`、
  `cmr_eval_item`、`cmr_eval_record`、`cmr_user`
- 字典/菜单写入平台表 `sys_dict*` / `sys_menu`，业务模块不创建平台表
- Liquibase：`core/src/main/resources/db/changelog/cmr/001–021_*.xml`，由
  `module-cmr-changelog.xml` → `start` 的 `changelog-master.xml` 挂载；每个 changeSet
  带 `preConditions onFail="MARK_RAN"` 幂等；已执行 changeSet 禁止修改
  （004/005 属历史补幂等，已加 `validCheckSum ANY` 放行校验和；021 为 005 部分执行环境的逐列补齐）
- 默认 `spring.liquibase.enabled=false`，按环境开启；`local` profile 自动开启

## 6. 文件存储

`cmr.file.store` 支持 `local` / `s3`：

- `local` 仅本地开发（默认 `./data/files`）
- `s3` 指向 OSS/MinIO，需要 `bucket`、`endpoint`、`access-key`、`secret-key`

生产必须切 `s3`，未配置桶时上传返回稳定业务错误，不阻塞启动。

## 7. 前端

```
cd web
corepack pnpm@10.17.1 install
corepack pnpm@10.17.1 dev
```

- 默认开发端口 `9848`（`.env` 的 `VITE_PORT`）；`/hs-api` 代理到 `VITE_PROXY_TARGET`
  （默认 `http://127.0.0.1:8080`，本机 `local` 联调可覆盖为 `18080`）
- 部署路径固定 `/sdom-hsmty/`，qiankun 子应用名由 `VITE_QIANKUN_NAME` 控制
- 类型检查 + 构建：`corepack pnpm@10.17.1 build`
- 测试：`corepack pnpm@10.17.1 test`（vitest）
- 静态检查：`corepack pnpm@10.17.1 lint`
- 菜单与字典先用前端静态配置（`menu.ts` / `dict.ts`），并入平台总前端后改为平台下发
- 本地开发需先通过 UAP 门户建立会话（子应用不提供登录页），否则业务接口返回 401；
  使用后端 `local` profile 联调时无需 UAP 会话

## 8. 部署

`deploy/docker-compose.yml` + `deploy/.env.example`。生产使用 docker compose，
镜像 tag 由 Jenkins 生成；启动命令加 `-p cmr-line` 避免空 `COMPOSE_PROJECT_NAME`。

```
docker compose -p cmr-line --env-file deploy/.env -f deploy/docker-compose.yml up -d
```

## 9. 验证状态（2026-11-11）

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 后端构建 | `mvn -s settings.xml -DskipTests -pl hsmty-module-cmr-core,hsmty-module-cmr-start -am package` | 通过 |
| 后端测试 | `mvn -s settings.xml -DskipTests=false -pl hsmty-module-cmr-start -am test` | 19 个用例：18 通过 + 1 个算子联调用例默认 `@Disabled` |
| 407/408 真实算子联调 | 本地 6300 算子 + `local` profile | 通过：歧义命中/过短提示/正常查询；检索返回真实条目并落库 |
| 前端类型检查 + 构建 | `corepack pnpm@10.17.1 build` | 通过 |
| 前端测试 | `corepack pnpm@10.17.1 test` | 7/7 通过 |
| 前端静态检查 | `corepack pnpm@10.17.1 lint` | 0 error |

## 10. 待确认事项

见 `docs/待提供清单.md`；与平台模板的差异见 `docs/对齐偏差登记.md`。
