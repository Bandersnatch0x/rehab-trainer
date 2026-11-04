from datetime import datetime

from pydantic import Field, model_validator

from app.core.timeutils import to_utc_naive
from app.schemas.common import APIModel
from app.schemas.enums import PrescriptionStatus
from app.schemas.exercise import Dosage


class PrescriptionItemIn(APIModel):
    exercise_code: str = Field(min_length=1)
    variant_code: str | None = None
    dosage: Dosage | None = None


class PrescriptionCreate(APIModel):
    patient_id: str = Field(min_length=1)
    items: list[PrescriptionItemIn] = Field(min_length=1)
    issued_at: datetime
    valid_from: datetime
    valid_until: datetime
    mapping_table_version: str = Field(min_length=1, max_length=32)

    @model_validator(mode="after")
    def _check_validity_window(self) -> "PrescriptionCreate":
        # 归一化后再比较，避免带时区与不带时区的输入无法直接比较。
        until = to_utc_naive(self.valid_until)
        start = to_utc_naive(self.valid_from)
        if until is not None and start is not None and until < start:
            raise ValueError("validUntil 不得早于 validFrom")
        return self


class PrescriptionPatch(APIModel):
    items: list[PrescriptionItemIn] | None = None
    valid_until: datetime | None = None
    note: str | None = None


class PrescriptionItemOut(APIModel):
    exercise_code: str
    variant_code: str | None = None
    dosage: Dosage | None = None


class PrescriptionOut(APIModel):
    id: str
    patient_id: str
    prescribed_by: str
    items: list[PrescriptionItemOut] = Field(default_factory=list)
    issued_at: datetime
    valid_from: datetime | None = None
    valid_until: datetime | None = None
    version: str
    mapping_table_version: str | None = None
    status: PrescriptionStatus
