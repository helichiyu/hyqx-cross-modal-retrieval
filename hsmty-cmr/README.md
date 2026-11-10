# hsmty-module-cmr-server（跨模态检索中心）

按 HSMTY 平台基线（`client → core → start`）搭建的新工程，与仓库根目录的
`cmr-backend` / `cmr-web` 并存，不覆盖原有代码。业务算子尚未迁移，当前交付的是
可编译、可启动、可测试的架构骨架 + 文件对象能力。

## 1. 目录

```
hsmty-cmr/
├── hsmty-module-cmr-client/   对外契约：Feign、DTO、VO
├── hsmty-module-cmr-core/     业务实现：Controller / Service / Mapper / PO / Liquibase
├── hsmty-module-cmr-start/    启动类、全局配置、application.yml、集成测试
├── web/                       qiankun 子应用 cross-modal-search（前缀 cms）
├── deploy/                    docker compose 编排与 .env 样例
├── settings.example.xml       Maven 设置模板（复制为 settings.xml 并填凭据，settings.xml 不入库）
└── Dockerfile                 后端镜像（Jenkins 构建后打包）
```

后端根包 `com.hsmty.cmr`，启动类为 `com.hsmty.RunApplication`；前端路由前缀 `/cms`，
部署路径 `/sdom-hsmty/`。

## 2. 获取平台依赖

平台公共能力（父 POM、公共库）来自公司内网：

- 模板仓库：`http://192.168.1.110:13000/hsmty/hsmty-module-cms-server`
- Maven 仓库：`http://192.168.1.110:13001/repository/maven-public/`
- 父工程：`com.hsmty:hsmty-module-starter:2.0.1`（本工程 `revision` 必须与父工程一致，父 POM 内 `${project.version}` 会按子工程版本插值）

首次使用复制脱敏模板并填入自己的 Nexus 账号（`settings.xml` 已被 git 忽略）：

```
cp settings.example.xml settings.xml
```

之后 CLI 构建统一使用 `mvn -s settings.xml`。只拉取依赖时通常可匿名访问公司 Nexus。

## 3. 构建与运行

首次构建：

```
mvn -s settings.xml -DskipTests clean package
```

如本机无法解析平台依赖，可显式指定本地仓库（公司模板在 `/ruanjian/maven/repository`）：

```
mvn -s settings.xml -Dmaven.repo.local=C:/Users/<user>/.m2/repository -DskipTests clean package
```

执行测试（父工程默认 `skipTests=true`，必须显式关闭）：

```
mvn -s settings.xml -DskipTests=false test
```

启动：

```
java -jar hsmty-module-cmr-start/target/hsmty-module-cmr-start.jar
```

- 端口 `8080`，上下文 `/hs-api`
- 接口文档 `/hs-api/swagger-ui.html`，OpenAPI `/hs-api/v3/api-docs`
- 业务接口统一为 `/hs-api/cmr/**`

本地执行数据库变更：

```
java -jar hsmty-module-cmr-start/target/hsmty-module-cmr-start.jar --spring.liquibase.enabled=true
```

## 4. 接口与权限

| 接口 | 方法 | 权限码 |
| --- | --- | --- |
| `/hs-api/cmr/files` | POST（上传） | `cmr:file:upload` |
| `/hs-api/cmr/files` | GET（分页） | `cmr:file:page` |
| `/hs-api/cmr/files/{id}` | GET（详情） | `cmr:file:detail` |
| `/hs-api/cmr/files/{id}/content` | GET（下载） | `cmr:file:read` |
| `/hs-api/cmr/files` | DELETE（批量删除） | `cmr:file:remove` |
| `/hs-api/cmr/files/by-ids` | POST（Feign 批量查） | `cmr:file:read` |
| `/hs-api/cmr/search/understand` | POST（407 查询理解代理） | `cmr:search:create` |

返回结构为平台 `ApiResult`：成功 `code=0000`，业务失败码带前缀（如 `C1003` 文件不存在、
`C108` 权限不足），分页为 `current/limit/total/totalPage/rows`。

### 检索算子

算子保持独立容器（决策 D-006），后端通过 Feign 调用，地址只来自配置：

| 配置 | 本地/测试默认 | 生产 |
| --- | --- | --- |
| `cmr.operator.base-url` | `http://127.0.0.1:6300` | 环境变量 `CMR_OPERATOR_BASE_URL`（compose 内 `http://cmr-operators:6300`） |
| 在线链超时 | `connect 2s / read 15s` | 同左，按需覆盖 |
| 批量链超时 | 预留 `1800000ms`，接入 401–406/409 时启用 | 同左 |

目前已接入 407 查询理解（`/hs-api/cmr/search/understand`）；408 检索需要结果集、任务与策略落库，随检索域迁移接入。

## 5. 数据库

- 库：`sd_data_engineering`（与其它模块共用，按表前缀隔离）
- 表：`cmr_file_object`
- Liquibase：`core/src/main/resources/db/changelog/cmr/001_cmr_file_object_init.xml`，
  由 `module-cmr-changelog.xml` → `start` 的 `changelog-master.xml` 挂载
- 默认 `spring.liquibase.enabled=false`，按环境开启

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

- 开发端口 `9848`，`/hs-api` 代理到 `http://127.0.0.1:8080`
- 类型检查 + 构建：`corepack pnpm@10.17.1 build`
- 测试：`corepack pnpm@10.17.1 test`（vitest）
- 静态检查：`corepack pnpm@10.17.1 lint`
- 检索页为占位：算子接口未迁移，不在前端伪造检索结果

## 8. 部署

`deploy/docker-compose.yml` + `deploy/.env.example`。生产使用 docker compose，
镜像 tag 由 Jenkins 生成；启动命令加 `-p cmr-line` 避免空 `COMPOSE_PROJECT_NAME`。

```
docker compose -p cmr-line --env-file deploy/.env -f deploy/docker-compose.yml up -d
```

## 9. 验证状态（2026-11-10）

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 后端测试 | `mvn -s settings.xml -DskipTests=false -pl hsmty-module-cmr-start -am test` | 12/12 通过（core 2、start 10） |
| 前端类型检查 + 构建 | `corepack pnpm@10.17.1 build` | 通过 |
| 前端测试 | `corepack pnpm@10.17.1 test` | 2/2 通过 |
| 前端静态检查 | `corepack pnpm@10.17.1 lint` | 0 error |

测试用 H2 + 白名单 `/**`，不依赖真实数据库与 Redis；生产启动需按 `docs/待提供清单.md` 注入配置。

## 10. 待确认事项

见 `docs/待提供清单.md`；与平台模板的差异见 `docs/对齐偏差登记.md`。
