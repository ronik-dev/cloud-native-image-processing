FROM python:3.13-slim AS builder

RUN pip install uv

WORKDIR /build

COPY worker/pyproject.toml .
COPY worker/uv.lock .

RUN uv sync --no-dev

FROM python:3.13-slim AS runtime

RUN apt-get update && apt-get install -y \
		ffmpeg \
		curl \
		&& rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/.venv /app/.venv
COPY worker/src/ .

RUN addgroup --system appgroup && adduser --system --group --home /home/appuser appuser
RUN mkdir -p /data/imageprocessing /home/appuser/.cache/huggingface /home/appuser/.u2net &&\
    chown -R appuser:appgroup /app /data/imageprocessing /home/appuser
USER appuser

EXPOSE 8082
ENTRYPOINT ["/app/.venv/bin/python", "main.py"]
