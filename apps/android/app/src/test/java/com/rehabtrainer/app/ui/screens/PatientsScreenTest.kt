package com.rehabtrainer.app.ui.screens

import com.rehabtrainer.app.data.contract.Patient
import com.rehabtrainer.app.data.contract.PatientStatus
import com.rehabtrainer.app.data.contract.StageConfirmationStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PatientsScreenTest {

    private val today = LocalDate.of(2026, 11, 7)

    private fun patient(displayName: String, birthYear: Int?) = Patient(
        id = "p-1",
        orgId = "org-1",
        displayName = displayName,
        hyStage = 2,
        stageConfirmationStatus = StageConfirmationStatus.CONFIRMED,
        status = PatientStatus.ACTIVE,
        birthYear = birthYear
    )

    @Test
    fun `shows name with age when birth year is known`() {
        assertEquals("张三 (78 岁)", profileTitle(patient("张三", 1948), today))
    }

    @Test
    fun `shows name only when birth year is missing`() {
        assertEquals("张三", profileTitle(patient("张三", null), today))
    }

    @Test
    fun `implausible birth year does not produce an age`() {
        // 出生年份被打错（未来年份或百岁以上）时只显示姓名，不显示荒谬年龄。
        assertEquals("张三", profileTitle(patient("张三", 2030), today))
        assertEquals("张三", profileTitle(patient("张三", 1800), today))
    }

    @Test
    fun `unbound shows a neutral label instead of a blank`() {
        assertEquals("未绑定患者", profileTitle(null, today))
        assertEquals("未绑定患者", profileTitle(patient("   ", null), today))
    }
}
