FROM python:3.13-slim-bookworm AS builder
WORKDIR /build
COPY services/api/pyproject.toml ./
COPY services/api/app ./app
COPY deploy/requirements.txt ./deploy-requirements.txt
RUN python -m pip wheel --no-cache-dir --wheel-dir /wheels '.[postgres]' -r deploy-requirements.txt

FROM python:3.13-slim-bookworm AS runtime
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    REHAB_AUTO_CREATE_SCHEMA=false
WORKDIR /opt/rehab
COPY --from=builder /wheels /wheels
# hadolint DL3013：不逐个写死版本，是因为 wheel 由上一阶段按 pyproject 与
# deploy/requirements.txt 构建后本地安装（--no-index），版本已在构建产物层固定。
# hadolint ignore=DL3013
RUN python -m pip install --no-cache-dir --no-index --find-links=/wheels rehab-trainer-api alembic 'psycopg[binary]' \
    && rm -rf /wheels \
    && groupadd --gid 10001 rehab \
    && useradd --uid 10001 --gid rehab --no-create-home rehab \
    && mkdir /data && chown rehab:rehab /data
COPY deploy /opt/rehab/deploy
USER 10001:10001
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD ["python", "/opt/rehab/deploy/healthcheck.py"]
CMD ["sh", "-c", "alembic -c deploy/alembic.ini upgrade head && exec uvicorn app.main:app --host 0.0.0.0 --port 8000"]
