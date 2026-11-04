from pydantic import Field

from app.schemas.common import APIModel
from app.schemas.enums import Role


class BindRequest(APIModel):
    binding_token: str = Field(min_length=1)
    device_id: str | None = None


class LoginRequest(APIModel):
    username: str = Field(min_length=1)
    password: str = Field(min_length=1)
    # TODO: 待实现：TOTP 二次验证（契约字段已预留）。
    totp: str | None = None


class RefreshRequest(APIModel):
    refresh_token: str = Field(min_length=1)


class TokenPair(APIModel):
    access_token: str
    refresh_token: str
    expires_in: int


class Me(APIModel):
    account_id: str
    display_name: str
    roles: list[Role]
    org_id: str
    org_name: str | None = None


class LoginResponse(TokenPair):
    profile: Me
