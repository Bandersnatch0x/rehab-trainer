"""审计与评估查询的复合索引（按机构/患者 + 时间倒序分页）。"""
from alembic import op

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade():
    op.create_index(
        "ix_audit_event_org_created",
        "audit_event",
        ["org_id", "created_at", "id"],
    )
    op.create_index(
        "ix_assessment_patient_assessed",
        "assessment",
        ["patient_id", "assessed_at", "id"],
    )


def downgrade():
    op.drop_index("ix_assessment_patient_assessed", table_name="assessment")
    op.drop_index("ix_audit_event_org_created", table_name="audit_event")