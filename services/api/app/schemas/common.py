from typing import Any

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class APIModel(BaseModel):
    """入参接受 camelCase 与 snake_case；出参统一 camelCase。"""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        from_attributes=True,
    )


class ErrorBody(APIModel):
    code: str
    message: str
    detail: dict[str, Any] | None = None
