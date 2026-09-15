package com.daoqiji.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExpiryParserTest {
    @Test
    fun usesConfiguredDefaultForNewInput() {
        assertEquals(7, ExpiryParser.parse("身份证2030年1月1日到期", 7)!!.remindBeforeDays)
        assertEquals(0, ExpiryParser.parse("身份证2030年1月1日到期", 0)!!.remindBeforeDays)
    }

    @Test
    fun explicitAdvanceOverridesConfiguredDefault() {
        assertEquals(90, ExpiryParser.parse("护照2029-05-10提前3个月提醒", 7)!!.remindBeforeDays)
    }

    @Test
    fun parsesIsoDateAndMonthReminder() {
        val result = ExpiryParser.parse("护照 2029-05-10 提前3个月提醒")

        assertNotNull(result)
        assertEquals("护照", result!!.title)
        assertEquals("2029-05-10", result.expireDate)
        assertEquals(90, result.remindBeforeDays)
    }

    @Test
    fun parsesChineseDateAndDayReminder() {
        val result = ExpiryParser.parse("Netflix 2026年7月20日到期 提前7天提醒")

        assertNotNull(result)
        assertEquals("Netflix", result!!.title)
        assertEquals("2026-07-20", result.expireDate)
        assertEquals(7, result.remindBeforeDays)
    }

    @Test
    fun parsesSpokenDaySuffix() {
        val result = ExpiryParser.parse("信用卡2027年11月20号到期提前1个月提醒")

        assertNotNull(result)
        assertEquals("信用卡", result!!.title)
        assertEquals("2027-11-20", result.expireDate)
        assertEquals(30, result.remindBeforeDays)
    }

    @Test
    fun usesLastDayWhenOnlyYearAndMonthAreProvided() {
        val result = ExpiryParser.parse("护照 2028年7月到期 提前6个月提醒")

        assertNotNull(result)
        assertEquals("护照", result!!.title)
        assertEquals("2028-07-31", result.expireDate)
        assertEquals(180, result.remindBeforeDays)
    }

    @Test
    fun parsesChineseNumeralMonthReminder() {
        val result = ExpiryParser.parse("护照 2028年7月8日到期 提前六个月提醒")

        assertNotNull(result)
        assertEquals("护照", result!!.title)
        assertEquals(180, result.remindBeforeDays)
    }

    @Test
    fun parsesFinancialChineseNumeralMonthReminder() {
        val result = ExpiryParser.parse("护照 2028年7月8日到期 提前陆个月提醒")

        assertNotNull(result)
        assertEquals(180, result!!.remindBeforeDays)
    }

    @Test
    fun parsesHalfYearReminder() {
        val result = ExpiryParser.parse("护照 2028年7月8日到期 提前半年提醒")

        assertNotNull(result)
        assertEquals("护照", result!!.title)
        assertEquals(180, result.remindBeforeDays)
    }

    @Test
    fun parsesWeekReminderFromOfflineSpeech() {
        val result = ExpiryParser.parse("妈妈生日2027年5月20日提前1周提醒", 30)

        assertNotNull(result)
        assertEquals("妈妈生日", result!!.title)
        assertEquals("2027-05-20", result.expireDate)
        assertEquals(7, result.remindBeforeDays)
    }

    @Test
    fun parsesChineseNumeralDateFromOfflineSpeech() {
        val result = ExpiryParser.parse("护照二零二八年七月八日到期提前半年提醒")

        assertNotNull(result)
        assertEquals("护照", result!!.title)
        assertEquals("2028-07-08", result.expireDate)
        assertEquals(180, result.remindBeforeDays)
    }

    @Test
    fun removesShelfLifeDateCueFromTitle() {
        val result = ExpiryParser.parse("酸奶保质期到2026年10月15日提前3天提醒")

        assertNotNull(result)
        assertEquals("酸奶", result!!.title)
        assertEquals("2026-10-15", result.expireDate)
        assertEquals(3, result.remindBeforeDays)
    }

    @Test
    fun defaultsReminderToThirtyDays() {
        val result = ExpiryParser.parse("身份证 2030年1月1日到期")

        assertNotNull(result)
        assertEquals("身份证", result!!.title)
        assertEquals("2030-01-01", result.expireDate)
        assertEquals(30, result.remindBeforeDays)
    }

    @Test
    fun returnsNullWhenNoDateExists() {
        val result = ExpiryParser.parse("提醒我护照")

        assertEquals(null, result)
    }

    @Test
    fun parsesYearlyRepeatFromSentence() {
        val result = ExpiryParser.parse("妈妈生日2027年5月20日，每年提前7天提醒")

        assertNotNull(result)
        assertEquals("妈妈生日", result!!.title)
        assertEquals(RepeatRule.Yearly, result.repeatRule)
    }

    @Test
    fun parsesMultipleReminderPointsFromOneSentence() {
        val result = ExpiryParser.parse("护照2030年10月20日到期，提前30天、7天和1天提醒")

        assertNotNull(result)
        assertEquals(30, result!!.remindBeforeDays)
        assertEquals(listOf(7, 1), result.additionalRemindBeforeDays)
        assertEquals("护照", result.title)
    }

    @Test
    fun parsesAdvanceWithoutTrailingReminderWord() {
        val result = ExpiryParser.parse("护照2030年10月20日到期，提前7天", 30)

        assertNotNull(result)
        assertEquals("护照", result!!.title)
        assertEquals(7, result.remindBeforeDays)
    }
}
