FROM python:3.12-slim

WORKDIR /app

# 固定环境变量进镜像（平台规范 4.1）：日志实时输出、不落 pyc
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 只打包算子服务：router + common + 9 个算子 + config.yaml/map.json
# 构建上下文即 operators/，镜像内落到 /app/operators，运行时以包方式导入
COPY . /app/operators/

# 模型权重不进镜像：compose 挂载宿主目录到 /weight，config.yaml 的 model_root 指向 /weight
# （不声明 VOLUME：平台要求运行期不在服务器留匿名卷，挂载一律由部署清单显式给出）

EXPOSE 6300

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD python -c "import httpx; httpx.get('http://127.0.0.1:6300/healthz', timeout=3).raise_for_status()"

CMD ["python", "-m", "uvicorn", "operators.router.main:app", "--host", "0.0.0.0", "--port", "6300"]
