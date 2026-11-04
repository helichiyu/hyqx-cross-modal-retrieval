# 一体化部署入口

- 编排文件：[docker-compose.yml](docker-compose.yml)（MySQL + Redis + 算子 + 后端 + 前端 nginx）
- 配置模板：[.env.example](.env.example)（复制为 `.env` 后修改）
- 完整步骤与运维说明：[../docs/部署与运维手册.md](../docs/部署与运维手册.md)

快速开始（在仓库根目录执行）：

```bash
cp deploy/.env.example deploy/.env
```

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

访问 `http://<宿主机IP>:8080`；算子 6300 不对宿主机暴露（仅内部网络）。
