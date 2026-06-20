FROM python:3.13-slim AS builder

RUN pip install uv

WORKDIR /build

COPY worker/pyproject.toml .
COPY worker/uv.lock .

RUN uv sync --no-dev

FROM python:3.13-slim AS runtime

RUN apt-get update && apt-get install -y \
		ffmpeg \
		&& rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/.venv /app/.venv
COPY worker/src/ .

EXPOSE 8082
ENTRYPOINT ["/app/.venv/bin/python", "main.py"]
