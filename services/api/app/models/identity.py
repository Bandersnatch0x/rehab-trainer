from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base
from app.core.timeutils import utcnow


class Org(Base):
    __tablename__ = "org"

    org_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    org_name: Mapped[str] = mapped_column(String(255), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)


class Account(Base):
    __tablename__ = "account"

    account_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    # 多机构：账号必须属于且仅属于一个机构（D10）。
    org_id: Mapped[str] = mapped_column(
        ForeignKey("org.org_id"), nullable=False, index=True
    )
    username: Mapped[str | None] = mapped_column(
        String(128), unique=True, nullable=True
    )
    display_name: Mapped[str] = mapped_column(String(128), nullable=False)
    password_hash: Mapped[str | None] = mapped_column(String(255), nullable=True)
    status: Mapped[str] = mapped_column(String(16), default="active", nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)


class RoleBinding(Base):
    __tablename__ = "role_binding"
    __table_args__ = (
        UniqueConstraint(
            "account_id", "role", "patient_id", name="uq_role_binding_account_role_patient"
        ),
    )

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    account_id: Mapped[str] = mapped_column(
        ForeignKey("account.account_id"), nullable=False, index=True
    )
    role: Mapped[str] = mapped_column(String(32), nullable=False)
    org_id: Mapped[str] = mapped_column(
        ForeignKey("org.org_id"), nullable=False, index=True
    )
    # 患者角色指向本人；照护者角色指向被照护患者；机构角色为空。
    patient_id: Mapped[str | None] = mapped_column(
        ForeignKey("patient.patient_id"), nullable=True, index=True
    )
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)


class BindingToken(Base):
    __tablename__ = "binding_token"

    token: Mapped[str] = mapped_column(String(128), primary_key=True)
    account_id: Mapped[str] = mapped_column(
        ForeignKey("account.account_id"), nullable=False, index=True
    )
    org_id: Mapped[str] = mapped_column(
        ForeignKey("org.org_id"), nullable=False, index=True
    )
    patient_id: Mapped[str | None] = mapped_column(
        ForeignKey("patient.patient_id"), nullable=True
    )
    expires_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    used_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)


class ExternalIdentity(Base):
    """微信 openid 等外部凭据，可撤销（T2）；openid 不是人的主键。"""

    __tablename__ = "external_identity"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    account_id: Mapped[str] = mapped_column(
        ForeignKey("account.account_id"), nullable=False, index=True
    )
    provider: Mapped[str] = mapped_column(String(32), nullable=False)
    app_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    openid: Mapped[str] = mapped_column(String(128), nullable=False)
    unionid: Mapped[str | None] = mapped_column(String(128), nullable=True)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)
