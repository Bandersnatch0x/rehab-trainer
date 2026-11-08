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


class TotpStatus(APIModel):
    totp_enabled: bool


class TotpEnrollment(APIModel):
    """待确认的密钥；确认成功前不落库。"""

    secret: str
    provisioning_uri: str


class TotpConfirmRequest(APIModel):
    secret: str = Field(min_length=16)
    code: str = Field(pattern=r"^[0-9]{6}$")


class TotpDisableRequest(APIModel):
    password: str = Field(min_length=1)
    code: str = Field(pattern=r"^[0-9]{6}$")
