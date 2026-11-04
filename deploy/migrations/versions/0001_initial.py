"""初始模型结构快照。"""
from alembic import op
import sqlalchemy as sa

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

def upgrade():
    op.create_table('exercise',
    sa.Column('code', sa.String(length=64), nullable=False),
    sa.Column('label', sa.String(length=128), nullable=False),
    sa.Column('category', sa.String(length=32), nullable=False),
    sa.Column('publication_status', sa.String(length=16), nullable=False),
    sa.Column('risk_level', sa.String(length=16), nullable=False),
    sa.Column('applicable_stages', sa.JSON(), nullable=False),
    sa.Column('body_parts', sa.JSON(), nullable=False),
    sa.Column('joints', sa.JSON(), nullable=False),
    sa.Column('contraindications', sa.JSON(), nullable=False),
    sa.Column('danger_signals', sa.JSON(), nullable=False),
    sa.Column('default_dosage', sa.JSON(), nullable=True),
    sa.Column('video_uri', sa.String(length=512), nullable=True),
    sa.Column('audio_uri', sa.String(length=512), nullable=True),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.PrimaryKeyConstraint('code')
    )
    op.create_table('org',
    sa.Column('org_id', sa.String(length=64), nullable=False),
    sa.Column('org_name', sa.String(length=255), nullable=False),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.PrimaryKeyConstraint('org_id')
    )
    op.create_table('account',
    sa.Column('account_id', sa.String(length=64), nullable=False),
    sa.Column('org_id', sa.String(length=64), nullable=False),
    sa.Column('username', sa.String(length=128), nullable=True),
    sa.Column('display_name', sa.String(length=128), nullable=False),
    sa.Column('password_hash', sa.String(length=255), nullable=True),
    sa.Column('status', sa.String(length=16), nullable=False),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.ForeignKeyConstraint(['org_id'], ['org.org_id'], ),
    sa.PrimaryKeyConstraint('account_id'),
    sa.UniqueConstraint('username')
    )
    op.create_index(op.f('ix_account_org_id'), 'account', ['org_id'], unique=False)
    op.create_table('exercise_variant',
    sa.Column('code', sa.String(length=64), nullable=False),
    sa.Column('exercise_code', sa.String(length=64), nullable=False),
    sa.Column('level', sa.Integer(), nullable=False),
    sa.Column('support_level', sa.String(length=32), nullable=False),
    sa.Column('applicable_stages', sa.JSON(), nullable=False),
    sa.Column('default_dosage', sa.JSON(), nullable=True),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.CheckConstraint('level BETWEEN 1 AND 3', name='ck_exercise_variant_level'),
    sa.ForeignKeyConstraint(['exercise_code'], ['exercise.code'], ),
    sa.PrimaryKeyConstraint('code')
    )
    op.create_index(op.f('ix_exercise_variant_exercise_code'), 'exercise_variant', ['exercise_code'], unique=False)
    op.create_table('patient',
    sa.Column('patient_id', sa.String(length=64), nullable=False),
    sa.Column('org_id', sa.String(length=64), nullable=False),
    sa.Column('display_name', sa.String(length=128), nullable=False),
    sa.Column('birth_year', sa.Integer(), nullable=True),
    sa.Column('hy_stage', sa.Integer(), nullable=False),
    sa.Column('stage_confirmation_status', sa.String(length=16), nullable=False),
    sa.Column('stage_confirmed_by', sa.String(length=64), nullable=True),
    sa.Column('stage_confirmed_at', sa.DateTime(), nullable=True),
    sa.Column('fall_risk', sa.String(length=16), nullable=True),
    sa.Column('symptoms', sa.JSON(), nullable=False),
    sa.Column('status', sa.String(length=16), nullable=False),
    sa.Column('note', sa.Text(), nullable=True),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.CheckConstraint('hy_stage BETWEEN 1 AND 5', name='ck_patient_hy_stage'),
    sa.ForeignKeyConstraint(['org_id'], ['org.org_id'], ),
    sa.PrimaryKeyConstraint('patient_id')
    )
    op.create_index(op.f('ix_patient_org_id'), 'patient', ['org_id'], unique=False)
    op.create_table('alert_event',
    sa.Column('id', sa.String(length=64), nullable=False),
    sa.Column('org_id', sa.String(length=64), nullable=False),
    sa.Column('patient_id', sa.String(length=64), nullable=False),
    sa.Column('rule_code', sa.String(length=64), nullable=False),
    sa.Column('level', sa.String(length=16), nullable=False),
    sa.Column('triggered_at', sa.DateTime(), nullable=False),
    sa.Column('status', sa.String(length=16), nullable=False),
    sa.Column('acknowledged_at', sa.DateTime(), nullable=True),
    sa.Column('handled_by', sa.String(length=64), nullable=True),
    sa.Column('note', sa.String(length=1024), nullable=True),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.ForeignKeyConstraint(['handled_by'], ['account.account_id'], ),
    sa.ForeignKeyConstraint(['org_id'], ['org.org_id'], ),
    sa.ForeignKeyConstraint(['patient_id'], ['patient.patient_id'], ),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_alert_event_org_id'), 'alert_event', ['org_id'], unique=False)
    op.create_index(op.f('ix_alert_event_patient_id'), 'alert_event', ['patient_id'], unique=False)
    op.create_table('assessment',
    sa.Column('id', sa.String(length=64), nullable=False),
    sa.Column('org_id', sa.String(length=64), nullable=False),
    sa.Column('patient_id', sa.String(length=64), nullable=False),
    sa.Column('instrument', sa.String(length=64), nullable=False),
    sa.Column('score', sa.Float(), nullable=True),
    sa.Column('answers', sa.JSON(), nullable=False),
    sa.Column('risk_level', sa.String(length=16), nullable=True),
    sa.Column('assessed_at', sa.DateTime(), nullable=False),
    sa.Column('assessed_by', sa.String(length=64), nullable=False),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.ForeignKeyConstraint(['assessed_by'], ['account.account_id'], ),
    sa.ForeignKeyConstraint(['org_id'], ['org.org_id'], ),
    sa.ForeignKeyConstraint(['patient_id'], ['patient.patient_id'], ),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_assessment_org_id'), 'assessment', ['org_id'], unique=False)
    op.create_index(op.f('ix_assessment_patient_id'), 'assessment', ['patient_id'], unique=False)
    op.create_table('audit_event',
    sa.Column('id', sa.String(length=64), nullable=False),
    sa.Column('org_id', sa.String(length=64), nullable=True),
    sa.Column('actor_account_id', sa.String(length=64), nullable=True),
    sa.Column('action', sa.String(length=64), nullable=False),
    sa.Column('resource_type', sa.String(length=64), nullable=True),
    sa.Column('resource_id', sa.String(length=64), nullable=True),
    sa.Column('outcome', sa.String(length=16), nullable=False),
    sa.Column('detail', sa.JSON(), nullable=False),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.ForeignKeyConstraint(['actor_account_id'], ['account.account_id'], ),
    sa.ForeignKeyConstraint(['org_id'], ['org.org_id'], ),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_audit_event_actor_account_id'), 'audit_event', ['actor_account_id'], unique=False)
    op.create_index(op.f('ix_audit_event_org_id'), 'audit_event', ['org_id'], unique=False)
    op.create_table('binding_token',
    sa.Column('token', sa.String(length=128), nullable=False),
    sa.Column('account_id', sa.String(length=64), nullable=False),
    sa.Column('org_id', sa.String(length=64), nullable=False),
    sa.Column('patient_id', sa.String(length=64), nullable=True),
    sa.Column('expires_at', sa.DateTime(), nullable=False),
    sa.Column('used_at', sa.DateTime(), nullable=True),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.ForeignKeyConstraint(['account_id'], ['account.account_id'], ),
    sa.ForeignKeyConstraint(['org_id'], ['org.org_id'], ),
    sa.ForeignKeyConstraint(['patient_id'], ['patient.patient_id'], ),
    sa.PrimaryKeyConstraint('token')
    )
    op.create_index(op.f('ix_binding_token_account_id'), 'binding_token', ['account_id'], unique=False)
    op.create_index(op.f('ix_binding_token_org_id'), 'binding_token', ['org_id'], unique=False)
    op.create_table('external_identity',
    sa.Column('id', sa.String(length=64), nullable=False),
    sa.Column('account_id', sa.String(length=64), nullable=False),
    sa.Column('provider', sa.String(length=32), nullable=False),
    sa.Column('app_id', sa.String(length=64), nullable=True),
    sa.Column('openid', sa.String(length=128), nullable=False),
    sa.Column('unionid', sa.String(length=128), nullable=True),
    sa.Column('revoked_at', sa.DateTime(), nullable=True),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.ForeignKeyConstraint(['account_id'], ['account.account_id'], ),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_external_identity_account_id'), 'external_identity', ['account_id'], unique=False)
    op.create_table('prescription',
    sa.Column('id', sa.String(length=64), nullable=False),
    sa.Column('org_id', sa.String(length=64), nullable=False),
    sa.Column('patient_id', sa.String(length=64), nullable=False),
    sa.Column('prescribed_by', sa.String(length=64), nullable=False),
    sa.Column('version', sa.String(length=32), nullable=False),
    sa.Column('mapping_table_version', sa.String(length=32), nullable=False),
    sa.Column('issued_at', sa.DateTime(), nullable=False),
    sa.Column('valid_from', sa.DateTime(), nullable=False),
    sa.Column('valid_until', sa.DateTime(), nullable=False),
    sa.Column('status', sa.String(length=16), nullable=False),
    sa.Column('note', sa.Text(), nullable=True),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.Column('updated_at', sa.DateTime(), nullable=False),
    sa.ForeignKeyConstraint(['org_id'], ['org.org_id'], ),
    sa.ForeignKeyConstraint(['patient_id'], ['patient.patient_id'], ),
    sa.ForeignKeyConstraint(['prescribed_by'], ['account.account_id'], ),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_prescription_org_id'), 'prescription', ['org_id'], unique=False)
    op.create_index(op.f('ix_prescription_patient_id'), 'prescription', ['patient_id'], unique=False)
    op.create_table('role_binding',
    sa.Column('id', sa.String(length=64), nullable=False),
    sa.Column('account_id', sa.String(length=64), nullable=False),
    sa.Column('role', sa.String(length=32), nullable=False),
    sa.Column('org_id', sa.String(length=64), nullable=False),
    sa.Column('patient_id', sa.String(length=64), nullable=True),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.ForeignKeyConstraint(['account_id'], ['account.account_id'], ),
    sa.ForeignKeyConstraint(['org_id'], ['org.org_id'], ),
    sa.ForeignKeyConstraint(['patient_id'], ['patient.patient_id'], ),
    sa.PrimaryKeyConstraint('id'),
    sa.UniqueConstraint('account_id', 'role', 'patient_id', name='uq_role_binding_account_role_patient')
    )
    op.create_index(op.f('ix_role_binding_account_id'), 'role_binding', ['account_id'], unique=False)
    op.create_index(op.f('ix_role_binding_org_id'), 'role_binding', ['org_id'], unique=False)
    op.create_index(op.f('ix_role_binding_patient_id'), 'role_binding', ['patient_id'], unique=False)
    op.create_table('stage_confirmation',
    sa.Column('id', sa.String(length=64), nullable=False),
    sa.Column('patient_id', sa.String(length=64), nullable=False),
    sa.Column('org_id', sa.String(length=64), nullable=False),
    sa.Column('stage', sa.Integer(), nullable=False),
    sa.Column('status', sa.String(length=16), nullable=False),
    sa.Column('confirmed_by', sa.String(length=64), nullable=False),
    sa.Column('confirmed_at', sa.DateTime(), nullable=False),
    sa.Column('note', sa.Text(), nullable=True),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.CheckConstraint('stage BETWEEN 1 AND 5', name='ck_stage_confirmation_stage'),
    sa.ForeignKeyConstraint(['confirmed_by'], ['account.account_id'], ),
    sa.ForeignKeyConstraint(['org_id'], ['org.org_id'], ),
    sa.ForeignKeyConstraint(['patient_id'], ['patient.patient_id'], ),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_stage_confirmation_org_id'), 'stage_confirmation', ['org_id'], unique=False)
    op.create_index(op.f('ix_stage_confirmation_patient_id'), 'stage_confirmation', ['patient_id'], unique=False)
    op.create_table('notification_delivery',
    sa.Column('id', sa.String(length=64), nullable=False),
    sa.Column('alert_id', sa.String(length=64), nullable=False),
    sa.Column('channel', sa.String(length=32), nullable=False),
    sa.Column('status', sa.String(length=16), nullable=False),
    sa.Column('delivered_at', sa.DateTime(), nullable=False),
    sa.Column('detail', sa.JSON(), nullable=False),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.ForeignKeyConstraint(['alert_id'], ['alert_event.id'], ),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_notification_delivery_alert_id'), 'notification_delivery', ['alert_id'], unique=False)
    op.create_table('prescription_item',
    sa.Column('id', sa.String(length=64), nullable=False),
    sa.Column('prescription_id', sa.String(length=64), nullable=False),
    sa.Column('exercise_code', sa.String(length=64), nullable=False),
    sa.Column('variant_code', sa.String(length=64), nullable=True),
    sa.Column('dosage', sa.JSON(), nullable=True),
    sa.Column('position', sa.Integer(), nullable=False),
    sa.ForeignKeyConstraint(['exercise_code'], ['exercise.code'], ),
    sa.ForeignKeyConstraint(['prescription_id'], ['prescription.id'], ),
    sa.ForeignKeyConstraint(['variant_code'], ['exercise_variant.code'], ),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_prescription_item_prescription_id'), 'prescription_item', ['prescription_id'], unique=False)
    op.create_table('session_record',
    sa.Column('id', sa.String(length=64), nullable=False),
    sa.Column('client_record_id', sa.String(length=128), nullable=False),
    sa.Column('org_id', sa.String(length=64), nullable=False),
    sa.Column('patient_id', sa.String(length=64), nullable=False),
    sa.Column('prescription_id', sa.String(length=64), nullable=False),
    sa.Column('recorded_by', sa.String(length=64), nullable=False),
    sa.Column('recorded_at', sa.DateTime(), nullable=False),
    sa.Column('checked_in', sa.Boolean(), nullable=False),
    sa.Column('completion_count', sa.Integer(), nullable=False),
    sa.Column('movement_error_count', sa.Integer(), nullable=False),
    sa.Column('interruption_count', sa.Integer(), nullable=False),
    sa.Column('valid_frame_ratio', sa.Float(), nullable=True),
    sa.Column('is_doubtful', sa.Boolean(), nullable=False),
    sa.Column('model_version', sa.String(length=64), nullable=False),
    sa.Column('model_sha256', sa.String(length=64), nullable=False),
    sa.Column('metric_algorithm_version', sa.String(length=64), nullable=False),
    sa.Column('threshold_config_version', sa.String(length=64), nullable=False),
    sa.Column('metrics', sa.JSON(), nullable=False),
    sa.Column('created_at', sa.DateTime(), nullable=False),
    sa.CheckConstraint('valid_frame_ratio IS NULL OR (valid_frame_ratio >= 0 AND valid_frame_ratio <= 1)', name='ck_session_record_valid_frame_ratio'),
    sa.ForeignKeyConstraint(['org_id'], ['org.org_id'], ),
    sa.ForeignKeyConstraint(['patient_id'], ['patient.patient_id'], ),
    sa.ForeignKeyConstraint(['prescription_id'], ['prescription.id'], ),
    sa.ForeignKeyConstraint(['recorded_by'], ['account.account_id'], ),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_session_record_client_record_id'), 'session_record', ['client_record_id'], unique=True)
    op.create_index(op.f('ix_session_record_org_id'), 'session_record', ['org_id'], unique=False)
    op.create_index(op.f('ix_session_record_patient_id'), 'session_record', ['patient_id'], unique=False)
    op.create_index(op.f('ix_session_record_prescription_id'), 'session_record', ['prescription_id'], unique=False)

def downgrade():
    op.drop_index(op.f('ix_session_record_prescription_id'), table_name='session_record')
    op.drop_index(op.f('ix_session_record_patient_id'), table_name='session_record')
    op.drop_index(op.f('ix_session_record_org_id'), table_name='session_record')
    op.drop_index(op.f('ix_session_record_client_record_id'), table_name='session_record')
    op.drop_table('session_record')
    op.drop_index(op.f('ix_prescription_item_prescription_id'), table_name='prescription_item')
    op.drop_table('prescription_item')
    op.drop_index(op.f('ix_notification_delivery_alert_id'), table_name='notification_delivery')
    op.drop_table('notification_delivery')
    op.drop_index(op.f('ix_stage_confirmation_patient_id'), table_name='stage_confirmation')
    op.drop_index(op.f('ix_stage_confirmation_org_id'), table_name='stage_confirmation')
    op.drop_table('stage_confirmation')
    op.drop_index(op.f('ix_role_binding_patient_id'), table_name='role_binding')
    op.drop_index(op.f('ix_role_binding_org_id'), table_name='role_binding')
    op.drop_index(op.f('ix_role_binding_account_id'), table_name='role_binding')
    op.drop_table('role_binding')
    op.drop_index(op.f('ix_prescription_patient_id'), table_name='prescription')
    op.drop_index(op.f('ix_prescription_org_id'), table_name='prescription')
    op.drop_table('prescription')
    op.drop_index(op.f('ix_external_identity_account_id'), table_name='external_identity')
    op.drop_table('external_identity')
    op.drop_index(op.f('ix_binding_token_org_id'), table_name='binding_token')
    op.drop_index(op.f('ix_binding_token_account_id'), table_name='binding_token')
    op.drop_table('binding_token')
    op.drop_index(op.f('ix_audit_event_org_id'), table_name='audit_event')
    op.drop_index(op.f('ix_audit_event_actor_account_id'), table_name='audit_event')
    op.drop_table('audit_event')
    op.drop_index(op.f('ix_assessment_patient_id'), table_name='assessment')
    op.drop_index(op.f('ix_assessment_org_id'), table_name='assessment')
    op.drop_table('assessment')
    op.drop_index(op.f('ix_alert_event_patient_id'), table_name='alert_event')
    op.drop_index(op.f('ix_alert_event_org_id'), table_name='alert_event')
    op.drop_table('alert_event')
    op.drop_index(op.f('ix_patient_org_id'), table_name='patient')
    op.drop_table('patient')
    op.drop_index(op.f('ix_exercise_variant_exercise_code'), table_name='exercise_variant')
    op.drop_table('exercise_variant')
    op.drop_index(op.f('ix_account_org_id'), table_name='account')
    op.drop_table('account')
    op.drop_table('org')
    op.drop_table('exercise')
