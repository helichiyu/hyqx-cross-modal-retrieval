# 跨模态检索中心 · 后端（构建于仓库根 deploy/docker-compose.yml 调用，context = cmr-backend）
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# 依赖层先缓存：仅 pom 变化时才重新下载
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy

# 供 HEALTHCHECK 与运维排障使用
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar

# 与算子容器共享的卷挂载点（compose 中绑定宿主机 dataset/ runtime/）
RUN mkdir -p /app/dataset /app/runtime

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/hs-api/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
