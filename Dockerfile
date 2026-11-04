FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY router/ router/
COPY operators/ operators/
COPY config.yaml map.json ./

# 模型权重不进镜像：挂载宿主目录到 /weight，config.yaml 的 model_root 指向 /weight
VOLUME ["/weight", "/app/runtime"]

EXPOSE 6300

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD python -c "import httpx; httpx.get('http://127.0.0.1:6300/healthz', timeout=3).raise_for_status()"

CMD ["python", "-m", "uvicorn", "router.main:app", "--host", "0.0.0.0", "--port", "6300"]
