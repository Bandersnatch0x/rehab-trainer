from enum import StrEnum


class Role(StrEnum):
    patient = "patient"
    caregiver = "caregiver"
    care_admin = "care_admin"
    rehab_therapist = "rehab_therapist"


class PatientStatus(StrEnum):
    active = "active"
    inactive = "inactive"


class StageConfirmationStatus(StrEnum):
    confirmed = "confirmed"
    suspected = "suspected"


class ExerciseCategory(StrEnum):
    stretching_relaxation = "stretching-relaxation"
    strength_maintenance = "strength-maintenance"
    rhythmic_gait = "rhythmic-gait"
    postural_correction = "postural-correction"


class RiskLevel(StrEnum):
    low = "low"
    medium = "medium"
    high = "high"


class PublicationStatus(StrEnum):
    draft = "draft"
    published = "published"
    retired = "retired"


class SupportLevel(StrEnum):
    unsupported_standing = "unsupported-standing"
    supported_standing = "supported-standing"
    seated = "seated"


class PrescriptionStatus(StrEnum):
    draft = "draft"
    active = "active"
    expired = "expired"


class AlertLevel(StrEnum):
    info = "info"
    warning = "warning"
    critical = "critical"


class AlertStatus(StrEnum):
    open = "open"
    acknowledged = "acknowledged"
    followed_up = "followed_up"
    closed = "closed"


class AlertActionStatus(StrEnum):
    acknowledged = "acknowledged"
    followed_up = "followed_up"
    closed = "closed"


class DeliveryChannel(StrEnum):
    in_app_popup = "in_app_popup"
    voice_prompt = "voice_prompt"
    push_notification = "push_notification"
    audit_log = "audit_log"


class DeliveryStatus(StrEnum):
    pending = "pending"
    sent = "sent"
    failed = "failed"
    fallback = "fallback"
