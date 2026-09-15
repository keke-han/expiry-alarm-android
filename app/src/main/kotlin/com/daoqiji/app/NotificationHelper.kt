package com.daoqiji.app

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    private const val CHANNEL_ID = "expiry_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "到期提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "提醒即将到期和当天到期的事项"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun showExpiryReminder(context: Context, item: ExpiryItem, leadDays: Int) {
        val isToday = leadDays == 0
        val title = if (isToday) "${item.title} 今天到期" else "${item.title} 快到期"
        val text = if (isToday) {
            "到期日：${item.expireDate}"
        } else {
            "到期日：${item.expireDate}，还有 $leadDays 天"
        }
        post(context, item.id.hashCode(), title, text)
    }

    fun showTestNotification(context: Context): Boolean = post(
        context, -20260706, "到期闹钟 · 测试通知", "如果你看到这条消息，说明本次通知已送达。"
    )

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, title: String, text: String): Boolean {
        ensureChannel(context)
        if (!canNotify(context)) return false
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }.isSuccess
    }
}
