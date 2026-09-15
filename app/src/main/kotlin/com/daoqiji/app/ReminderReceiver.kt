package com.daoqiji.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val today = LocalDate.now()
            ExpiryRepository(context).getAll().forEach { item ->
                item.leadDaysOn(today)?.let { leadDays ->
                    NotificationHelper.showExpiryReminder(
                        context,
                        item,
                        leadDays = leadDays
                    )
                }
            }
        } finally {
            ReminderScheduler.scheduleDailyCheck(context)
        }
    }
}
