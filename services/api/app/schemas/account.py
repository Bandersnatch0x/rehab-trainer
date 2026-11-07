from datetime import datetime
from enum import StrEnum

from pydantic import Field

from app.schemas.common import APIModel
from app.schemas.enums import Role


class AccountStatus(StrEnum):
    active = "active"
    disabled = "disabled"


class ManagedRole(StrEnum):
    care_admin = "care_admin"
    rehab_therapist = "rehab_therapist"


class AccountOut(APIModel):
    account_id: str
    display_name: str
    username: str | None = None
    status: AccountStatus
    roles: list[Role]
    created_at: datetime


class AccountPage(APIModel):
    items: list[AccountOut]
    next_cursor: str | None = None


class AccountCreate(APIModel):
    display_name: str = Field(min_length=1, max_length=128)
    username: str = Field(min_length=3, max_length=128)
    password: str = Field(min_length=8, max_length=128)
    role: ManagedRole


class AccountPatch(APIModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=128)
    status: AccountStatus | None = None
    role: ManagedRole | None = None
