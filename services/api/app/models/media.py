from datetime import datetime
from typing import Any

from sqlalchemy import (
    JSON,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base
from app.core.timeutils import utcnow


class MediaAsset(Base):
    """训练视频资产；上架后才参与下发，下架保留记录与文件。

    `storage_path` 相对配置的媒体静态根，`public_url` 是拼装后的可访问地址。
    两者都由服务端生成，不接受客户端输入（防路径穿越）。
    """

    __tablename__ = "media_asset"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'published', 'retired')",
            name="ck_media_asset_status",
        ),
    )

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    org_id: Mapped[str] = mapped_column(
        ForeignKey("org.org_id"), nullable=False, index=True
    )
    # 为空表示通用素材，不绑定具体训练条目。
    exercise_code: Mapped[str | None] = mapped_column(
        ForeignKey("exercise.code"), nullable=True, index=True
    )
    storage_path: Mapped[str] = mapped_column(String(512), nullable=False)
    public_url: Mapped[str] = mapped_column(String(512), nullable=False)
    content_type: Mapped[str] = mapped_column(String(64), nullable=False, default="video/mp4")
    size_bytes: Mapped[int] = mapped_column(Integer, nullable=False)
    sha256: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    watermark_text: Mapped[str | None] = mapped_column(String(128), nullable=True)
    credit_line: Mapped[str | None] = mapped_column(String(255), nullable=True)
    status: Mapped[str] = mapped_column(String(16), default="draft", nullable=False, index=True)
    # 按 H-Y 分期下发；空数组表示未裁决，不下发给任何分期。
    applicable_stages: Mapped[list[Any]] = mapped_column(JSON, default=list, nullable=False)
    published_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    retired_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_by: Mapped[str | None] = mapped_column(
        ForeignKey("account.account_id"), nullable=True
    )
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)
