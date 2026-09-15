package com.daoqiji.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {
    private const val REQUEST_CODE = 20260706

    fun scheduleDailyCheck(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextReminderMillis(
                LocalDateTime.now(),
                SettingsRepository(context).load().reminderTime,
                ZoneId.systemDefault()
            ),
            intent
        )
    }
}
