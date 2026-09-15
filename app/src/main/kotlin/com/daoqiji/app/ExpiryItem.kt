package com.daoqiji.app

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

enum class RepeatRule(val label: String) {
    None("不重复"), Yearly("每年重复")
}

enum class ItemCategory(val label: String) {
    Document("证件"), Card("卡片"), Membership("会员"), Food("食品"),
    Anniversary("纪念日"), Other("其他")
}

enum class ItemLifecycle { Active, Completed }

data class ExpiryItem(
    val id: String,
    val title: String,
    val expireDate: String,
    val remindBeforeDays: Int,
    val createdAt: String,
    val repeatRule: RepeatRule = RepeatRule.None,
    val category: ItemCategory = ItemCategory.Other,
    val additionalRemindBeforeDays: List<Int> = emptyList(),
    val lifecycle: ItemLifecycle = ItemLifecycle.Active,
    val renewalHistory: List<String> = emptyList()
) {
    fun expireLocalDate(): LocalDate = LocalDate.parse(expireDate)

    fun nextOccurrence(today: LocalDate = LocalDate.now()): LocalDate {
        val date = expireLocalDate()
        if (repeatRule == RepeatRule.None || !date.isBefore(today)) return date
        val monthDay = MonthDay.from(date)
        fun occurrence(year: Int): LocalDate = runCatching { monthDay.atYear(year) }
            .getOrElse { LocalDate.of(year, 2, 28) }
        val thisYear = occurrence(today.year)
        return if (thisYear.isBefore(today)) occurrence(today.year + 1) else thisYear
    }

    fun reminderDate(today: LocalDate = LocalDate.now()): LocalDate =
        nextOccurrence(today).minusDays(remindBeforeDays.toLong())

    fun allReminderDays(): List<Int> =
        (listOf(remindBeforeDays) + additionalRemindBeforeDays)
            .filter { it >= 0 }
            .distinct()
            .sortedDescending()

    fun shouldNotifyOn(today: LocalDate = LocalDate.now()): Boolean {
        return leadDaysOn(today) != null
    }

    fun leadDaysOn(today: LocalDate = LocalDate.now()): Int? {
        if (lifecycle != ItemLifecycle.Active) return null
        val days = ChronoUnit.DAYS.between(today, nextOccurrence(today)).toInt()
        return days.takeIf { it == 0 || it in allReminderDays() }
    }

    fun daysRemaining(today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, nextOccurrence(today))
}

fun ExpiryItem.renew(newExpireDate: String): ExpiryItem {
    LocalDate.parse(newExpireDate)
    return copy(
        expireDate = newExpireDate,
        lifecycle = ItemLifecycle.Active,
        renewalHistory = renewalHistory + expireDate
    )
}

fun inferCategory(title: String): ItemCategory = when {
    listOf("护照", "身份证", "驾驶证", "签证", "证件").any(title::contains) -> ItemCategory.Document
    listOf("信用卡", "银行卡", "卡片").any(title::contains) -> ItemCategory.Card
    listOf("会员", "Netflix", "奈飞", "订阅").any { title.contains(it, ignoreCase = true) } -> ItemCategory.Membership
    listOf("保质期", "食品", "牛奶", "酸奶", "药品").any(title::contains) -> ItemCategory.Food
    listOf("生日", "纪念日", "周年").any(title::contains) -> ItemCategory.Anniversary
    else -> ItemCategory.Other
}

enum class ExpiryStatus {
    Safe,
    Soon,
    Urgent,
    Expired
}

fun ExpiryItem.status(today: LocalDate = LocalDate.now()): ExpiryStatus {
    val days = daysRemaining(today)
    return when {
        days < 0 -> ExpiryStatus.Expired
        days < 30 -> ExpiryStatus.Urgent
        days <= 90 -> ExpiryStatus.Soon
        else -> ExpiryStatus.Safe
    }
}
