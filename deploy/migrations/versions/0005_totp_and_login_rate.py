"""二次验证与跨副本登录限流（TOTP + login_failure）。"""
from alembic import op
import sqlalchemy as sa

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade():
    # 可空列可直接 ADD COLUMN，SQLite 与 PostgreSQL 都支持。
    op.add_column("account", sa.Column("totp_secret", sa.String(length=64), nullable=True))
    op.add_column("account", sa.Column("totp_last_step", sa.Integer(), nullable=True))

    op.create_table(
        "login_failure",
        sa.Column("id", sa.String(length=64), nullable=False),
        sa.Column("username", sa.String(length=128), nullable=False),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_login_failure_username_created",
        "login_failure",
        ["username", "created_at"],
    )


def downgrade():
    op.drop_index("ix_login_failure_username_created", table_name="login_failure")
    op.drop_table("login_failure")
    op.drop_column("account", "totp_last_step")
    op.drop_column("account", "totp_secret")