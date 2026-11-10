from pydantic import Field

from app.schemas.common import APIModel
from app.schemas.enums import Role


class BindRequest(APIModel):
    binding_token: str = Field(min_length=1)
    device_id: str | None = None


class LoginRequest(APIModel):
    username: str = Field(min_length=1, max_length=128)
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


class TotpEnrollRequest(APIModel):
    """启用二次验证前必须再验证一次口令。"""

    password: str = Field(min_length=1)


class TotpEnrollment(APIModel):
    """待确认的密钥；确认成功前不落库。"""

    secret: str
    provisioning_uri: str
    # 短时签名凭证：confirm 只接受本次签发的密钥，客户端无法自行替换 secret。
    enrollment_token: str


class TotpConfirmRequest(APIModel):
    enrollment_token: str = Field(min_length=16)
    code: str = Field(pattern=r"^[0-9]{6}$")


class TotpDisableRequest(APIModel):
    password: str = Field(min_length=1)
    code: str = Field(pattern=r"^[0-9]{6}$")
