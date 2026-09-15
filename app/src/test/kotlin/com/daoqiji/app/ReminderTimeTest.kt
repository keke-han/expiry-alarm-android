package com.daoqiji.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderTimeTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun schedulesSelectedTimeTodayWhenItIsStillAhead() {
        val now = LocalDateTime.of(2026, 9, 4, 8, 0)
        assertEquals(now.withHour(18).withMinute(30).atZone(zone).toInstant().toEpochMilli(),
            nextReminderMillis(now, LocalTime.of(18, 30), zone))
    }

    @Test
    fun schedulesTomorrowWhenTimeHasPassedOrIsEqual() {
        val now = LocalDateTime.of(2026, 9, 4, 9, 0)
        val expected = now.plusDays(1).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, nextReminderMillis(now, LocalTime.of(9, 0), zone))
        assertEquals(expected, nextReminderMillis(now.plusMinutes(1), LocalTime.of(9, 0), zone))
    }

    @Test
    fun handlesYearBoundaryAndMidnight() {
        val now = LocalDateTime.of(2026, 12, 31, 23, 59)
        val expected = LocalDateTime.of(2027, 1, 1, 0, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, nextReminderMillis(now, LocalTime.MIDNIGHT, zone))
    }
}
