"""训练视频资产 media_asset（视频管理与按分期下发）。"""
from alembic import op
import sqlalchemy as sa

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "media_asset",
        sa.Column("id", sa.String(length=64), nullable=False),
        sa.Column("org_id", sa.String(length=64), nullable=False),
        sa.Column("exercise_code", sa.String(length=64), nullable=True),
        sa.Column("storage_path", sa.String(length=512), nullable=False),
        sa.Column("public_url", sa.String(length=512), nullable=False),
        sa.Column("content_type", sa.String(length=64), nullable=False),
        sa.Column("size_bytes", sa.Integer(), nullable=False),
        sa.Column("sha256", sa.String(length=64), nullable=False),
        sa.Column("watermark_text", sa.String(length=128), nullable=True),
        sa.Column("credit_line", sa.String(length=255), nullable=True),
        sa.Column("status", sa.String(length=16), nullable=False),
        sa.Column("applicable_stages", sa.JSON(), nullable=False),
        sa.Column("published_at", sa.DateTime(), nullable=True),
        sa.Column("retired_at", sa.DateTime(), nullable=True),
        sa.Column("created_by", sa.String(length=64), nullable=True),
        sa.Column("note", sa.Text(), nullable=True),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.Column("updated_at", sa.DateTime(), nullable=False),
        sa.CheckConstraint(
            "status IN ('draft', 'published', 'retired')",
            name="ck_media_asset_status",
        ),
        sa.ForeignKeyConstraint(["org_id"], ["org.org_id"]),
        sa.ForeignKeyConstraint(["exercise_code"], ["exercise.code"]),
        sa.ForeignKeyConstraint(["created_by"], ["account.account_id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_media_asset_org_id", "media_asset", ["org_id"])
    op.create_index("ix_media_asset_exercise_code", "media_asset", ["exercise_code"])
    op.create_index("ix_media_asset_sha256", "media_asset", ["sha256"])
    op.create_index("ix_media_asset_status", "media_asset", ["status"])


def downgrade():
    op.drop_index("ix_media_asset_status", table_name="media_asset")
    op.drop_index("ix_media_asset_sha256", table_name="media_asset")
    op.drop_index("ix_media_asset_exercise_code", table_name="media_asset")
    op.drop_index("ix_media_asset_org_id", table_name="media_asset")
    op.drop_table("media_asset")
