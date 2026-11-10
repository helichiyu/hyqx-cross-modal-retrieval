# 后端镜像：由 Jenkins 在完成 Maven 构建后打包
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY hsmty-module-cmr-start/target/hsmty-module-cmr-start.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
