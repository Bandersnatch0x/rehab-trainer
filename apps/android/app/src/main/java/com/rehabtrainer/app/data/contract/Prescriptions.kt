package com.rehabtrainer.app.data.contract

import kotlinx.serialization.Serializable

/** 契约 `PrescriptionItem`。 */
@Serializable
data class PrescriptionItem(
    val exerciseCode: String,
    val variantCode: String? = null,
    val dosage: Dosage? = null
)

/** 契约 `PrescriptionCreate`。 */
@Serializable
data class PrescriptionCreate(
    val patientId: String,
    val items: List<PrescriptionItem>,
    val issuedAt: String,
    val validFrom: String,
    val validUntil: String,
    val mappingTableVersion: String
)

/** 契约 `PrescriptionPatch`；`If-Match` 版本号在接口层单独传递。 */
@Serializable
data class PrescriptionPatch(
    val items: List<PrescriptionItem>? = null,
    val validUntil: String? = null,
    val note: String? = null
)

/** 契约 `Prescription`；`version` 为乐观锁版本。 */
@Serializable
data class Prescription(
    val id: String,
    val patientId: String,
    val prescribedBy: String,
    val items: List<PrescriptionItem>,
    val issuedAt: String,
    val version: String,
    val status: PrescriptionStatus,
    val validFrom: String? = null,
    val validUntil: String? = null,
    val mappingTableVersion: String? = null
)
