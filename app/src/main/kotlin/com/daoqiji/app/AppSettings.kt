package com.daoqiji.app

import android.content.Context
import java.time.LocalTime

enum class ThemeChoice(val label: String) {
    System("跟随系统"), Light("浅色"), Dark("深色")
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.System,
    val defaultRemindBeforeDays: Int = 30,
    val reminderTime: LocalTime = LocalTime.of(9, 0),
    val privacyAccepted: Boolean = false
)

internal fun canRequestSensitivePermission(privacyAccepted: Boolean): Boolean = privacyAccepted

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        theme = ThemeChoice.entries.firstOrNull {
            it.name == preferences.getString("theme", null)
        } ?: ThemeChoice.System,
        defaultRemindBeforeDays = preferences.getInt("default_days", 30).coerceIn(0, 3650),
        reminderTime = LocalTime.ofSecondOfDay(
            preferences.getInt("reminder_minute", 540).coerceIn(0, 1439) * 60L
        ),
        privacyAccepted = preferences.getBoolean("privacy_accepted", false)
    )

    fun save(settings: AppSettings) {
        require(settings.defaultRemindBeforeDays in 0..3650)
        preferences.edit()
            .putString("theme", settings.theme.name)
            .putInt("default_days", settings.defaultRemindBeforeDays)
            .putInt("reminder_minute", settings.reminderTime.hour * 60 + settings.reminderTime.minute)
            .putBoolean("privacy_accepted", settings.privacyAccepted)
            .apply()
    }
}
