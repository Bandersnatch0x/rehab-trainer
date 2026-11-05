from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.core.config import get_settings
from app.core.database import init_db
from app.core.errors import DEFAULT_ERROR_CODES
from app.routers import (
    alerts,
    assessments,
    audit,
    auth,
    exercises,
    media,
    patients,
    prescriptions,
    research,
    sessions,
)

settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    if settings.auto_create_schema:
        init_db()
    yield


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description=(
        "rehab-trainer 后端 API。契约唯一真源：packages/api-contract/openapi.yaml。"
        "所有资源访问按调用者机构隔离；跨机构访问返回 403 并写审计。"
    ),
    lifespan=lifespan,
)


@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(
    request: Request, exc: StarletteHTTPException
) -> JSONResponse:
    detail: Any = exc.detail
    if isinstance(detail, dict) and "code" in detail:
        content = detail
    else:
        content = {
            "code": DEFAULT_ERROR_CODES.get(exc.status_code, "ERROR"),
            "message": str(detail),
        }
    return JSONResponse(
        status_code=exc.status_code, content=content, headers=exc.headers
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    errors = [
        {
            "loc": list(error.get("loc", [])),
            "type": error.get("type", "value_error"),
            "msg": error.get("msg", "invalid value"),
        }
        for error in exc.errors()
    ]
    return JSONResponse(
        status_code=422,
        content={
            "code": "VALIDATION_FAILED",
            "message": "请求校验失败",
            "detail": {"errors": errors},
        },
    )


API_PREFIX = "/v1"
for router in (
    auth.router,
    patients.router,
    assessments.router,
    exercises.router,
    prescriptions.router,
    sessions.router,
    alerts.router,
    audit.router,
    research.router,
    media.router,
):
    app.include_router(router, prefix=API_PREFIX)

# 本地开发直接由 API 托管媒体文件；生产环境由 nginx 指向同一目录
# （见 deploy/nginx/rehab-media.conf），此时该挂载前缀不会被命中。
Path(settings.media_root).mkdir(parents=True, exist_ok=True)
app.mount("/media", StaticFiles(directory=settings.media_root), name="media")
