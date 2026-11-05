"""科研数据底座：patient.data_origin（N1）。"""
from alembic import op
import sqlalchemy as sa

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade():
    # batch 模式：SQLite 不支持 ALTER 加约束，需整表重建；PostgreSQL 走常规 ALTER。
    # server_default 让存量患者按 real 回填，新行仍由模型默认值接管。
    with op.batch_alter_table("patient", recreate="always") as batch:
        batch.add_column(
            sa.Column(
                "data_origin",
                sa.String(length=8),
                nullable=False,
                server_default="real",
            )
        )
        batch.create_index("ix_patient_data_origin", ["data_origin"])
        batch.create_check_constraint(
            "ck_patient_data_origin", "data_origin IN ('real', 'demo')"
        )


def downgrade():
    with op.batch_alter_table("patient", recreate="always") as batch:
        batch.drop_constraint("ck_patient_data_origin", type_="check")
        batch.drop_index("ix_patient_data_origin")
        batch.drop_column("data_origin")
