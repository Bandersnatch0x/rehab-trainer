from typing import Any

from fastapi import HTTPException

# 契约 Error schema：{code, message, detail?}
DEFAULT_ERROR_CODES = {
    400: "BAD_REQUEST",
    401: "UNAUTHORIZED",
    403: "FORBIDDEN",
    404: "NOT_FOUND",
    409: "CONFLICT",
    422: "VALIDATION_FAILED",
    429: "RATE_LIMITED",
}


def api_error(
    status_code: int,
    code: str,
    message: str,
    detail: dict[str, Any] | None = None,
) -> HTTPException:
    payload: dict[str, Any] = {"code": code, "message": message}
    if detail is not None:
        payload["detail"] = detail
    return HTTPException(status_code=status_code, detail=payload)
