package com.rehabtrainer.app.core.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionalVideoSourceTest {

    @Test
    fun 缺少地址时回退内置演示视频() {
        val source = InstructionalVideoSource(remoteUrl = null)

        assertFalse(source.shouldUseRemote)
        assertNull(source.urlAfterFailure(remoteFailed = false))
    }

    @Test
    fun 空白地址视为未下发() {
        assertFalse(InstructionalVideoSource(remoteUrl = "   ").shouldUseRemote)
        assertFalse(InstructionalVideoSource(remoteUrl = "").shouldUseRemote)
    }

    @Test
    fun 有地址时优先播放远端视频() {
        val source = InstructionalVideoSource(remoteUrl = " https://media.example/v.mp4 ")

        assertTrue(source.shouldUseRemote)
        assertEquals("https://media.example/v.mp4", source.urlAfterFailure(remoteFailed = false))
    }

    @Test
    fun 远端播放失败后固定回退() {
        val source = InstructionalVideoSource(remoteUrl = "https://media.example/v.mp4")

        assertNull(source.urlAfterFailure(remoteFailed = true))
    }

    @Test
    fun 空标注不渲染叠加层() {
        val source = InstructionalVideoSource(
            remoteUrl = "https://media.example/v.mp4",
            watermarkText = "  ",
            creditLine = ""
        )

        assertFalse(source.hasOverlay)
        assertNull(source.watermarkLabel)
        assertNull(source.creditLabel)
    }

    @Test
    fun 标注去除首尾空白后保留() {
        val source = InstructionalVideoSource(
            watermarkText = " 演示素材 ",
            creditLine = " 来源：示例机构 "
        )

        assertTrue(source.hasOverlay)
        assertEquals("演示素材", source.watermarkLabel)
        assertEquals("来源：示例机构", source.creditLabel)
    }
}
