package com.daoqiji.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechResultTest {
    @Test
    fun acceptsCompleteChineseRecognition() {
        assertEquals(
            "奈飞会员2026年7月20日到期提前7天提醒",
            usableSpeechResult(" 奈飞会员2026年7月20日到期提前7天提醒 ")
        )
    }

    @Test
    fun rejectsRecognitionContainingUnknownToken() {
        assertNull(usableSpeechResult("<unk> 会员2026年7月20日到期提前7天提醒"))
    }
}
