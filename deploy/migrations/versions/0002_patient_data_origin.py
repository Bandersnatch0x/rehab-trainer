"""科研数据底座：patient.data_origin（N1）。"""
from alembic import op
import sqlalchemy as sa

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def _is_sqlite() -> bool:
    """SQLite 不支持 ALTER 加约束，需要 batch 整表重建；其他方言走常规 ALTER。"""
    return op.get_bind().dialect.name == "sqlite"


def upgrade():
    column = sa.Column(
        "data_origin",
        sa.String(length=8),
        nullable=False,
        server_default="real",
    )
    if _is_sqlite():
        # recreate="always" 只在 SQLite 使用：加检查约束必须重建表。
        with op.batch_alter_table("patient", recreate="always") as batch:
            batch.add_column(column)
            batch.create_index("ix_patient_data_origin", ["data_origin"])
            batch.create_check_constraint(
                "ck_patient_data_origin", "data_origin IN ('real', 'demo')"
            )
        return

    # PostgreSQL 等：常规 ALTER，避免整表重建与锁表，且可生成离线 SQL。
    op.add_column("patient", column)
    op.create_index("ix_patient_data_origin", "patient", ["data_origin"])
    op.create_check_constraint(
        "ck_patient_data_origin", "patient", "data_origin IN ('real', 'demo')"
    )


def downgrade():
    if _is_sqlite():
        with op.batch_alter_table("patient", recreate="always") as batch:
            batch.drop_constraint("ck_patient_data_origin", type_="check")
            batch.drop_index("ix_patient_data_origin")
            batch.drop_column("data_origin")
        return

    op.drop_constraint("ck_patient_data_origin", "patient", type_="check")
    op.drop_index("ix_patient_data_origin", table_name="patient")
    op.drop_column("patient", "data_origin")