package com.daoqiji.app

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal fun nextReminderMillis(now: LocalDateTime, time: LocalTime, zone: ZoneId): Long {
    var next = now.toLocalDate().atTime(time)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return next.atZone(zone).toInstant().toEpochMilli()
}
