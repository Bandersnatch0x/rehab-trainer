from contextlib import asynccontextmanager
import logging
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy.exc import OperationalError
from sqlalchemy.exc import TimeoutError as PoolTimeoutError
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
logger = logging.getLogger(__name__)

HTTP_SERVICE_UNAVAILABLE = 503


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


@app.exception_handler(PoolTimeoutError)
async def pool_timeout_handler(request: Request, exc: PoolTimeoutError) -> JSONResponse:
    """连接池等待超时：快速失败，而不是让调用方挂到 pool_timeout 之后。

    这是过载信号，不是业务错误：客户端应稍后重试（Android 端训练记录本就有本地队列重试）。
    """
    logger.error(
        "连接池等待超时 %s %s：并发已超过 pool_size+max_overflow=%d",
        request.method,
        request.url.path,
        settings.db_pool_size + settings.db_max_overflow,
    )
    return JSONResponse(
        status_code=HTTP_SERVICE_UNAVAILABLE,
        content={
            "code": "SERVICE_UNAVAILABLE",
            "message": "服务繁忙，请稍后重试",
            "detail": {"retryable": True},
        },
    )


@app.exception_handler(OperationalError)
async def database_unavailable_handler(
    request: Request, exc: OperationalError
) -> JSONResponse:
    """数据库连接层面的故障（重启、切换、网络中断）。

    `pool_pre_ping` 已挡住"取到死连接"这一大类；这里兜住请求中途断连的情况。
    错误原文只进日志：库名、主机、连接串不能出现在响应里。
    """
    logger.error(
        "数据库连接异常 %s %s：%s",
        request.method,
        request.url.path,
        exc,
        exc_info=True,
    )
    return JSONResponse(
        status_code=HTTP_SERVICE_UNAVAILABLE,
        content={
            "code": "SERVICE_UNAVAILABLE",
            "message": "服务暂时不可用，请稍后重试",
            "detail": {"retryable": True},
        },
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
