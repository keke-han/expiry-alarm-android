package com.daoqiji.app

import java.time.LocalDate
import java.time.YearMonth

enum class ExpiryFilter(val label: String) {
    All("全部"), Within30Days("30天内"), Expired("已过期"), Completed("已处理")
}

data class ExpiryOverview(
    val active: Int,
    val within30Days: Int,
    val expired: Int,
    val completed: Int
)

fun filterExpiryItems(
    items: List<ExpiryItem>,
    query: String,
    filter: ExpiryFilter,
    today: LocalDate = LocalDate.now()
): List<ExpiryItem> = items.filter { item ->
    val matchesQuery = query.isBlank() || item.title.contains(query.trim(), ignoreCase = true)
    val days = item.daysRemaining(today)
    val matchesFilter = when (filter) {
        ExpiryFilter.All -> item.lifecycle == ItemLifecycle.Active
        ExpiryFilter.Within30Days -> item.lifecycle == ItemLifecycle.Active && days in 0..30
        ExpiryFilter.Expired -> item.lifecycle == ItemLifecycle.Active && item.repeatRule == RepeatRule.None && days < 0
        ExpiryFilter.Completed -> item.lifecycle == ItemLifecycle.Completed
    }
    matchesQuery && matchesFilter
}

fun groupExpiryItemsByMonth(
    items: List<ExpiryItem>,
    today: LocalDate = LocalDate.now()
): Map<YearMonth, List<ExpiryItem>> = items
    .sortedBy { it.nextOccurrence(today) }
    .groupBy { YearMonth.from(it.nextOccurrence(today)) }
    .toSortedMap()

fun expiryOverview(
    items: List<ExpiryItem>,
    today: LocalDate = LocalDate.now()
): ExpiryOverview = ExpiryOverview(
    active = items.count { it.lifecycle == ItemLifecycle.Active },
    within30Days = items.count {
        it.lifecycle == ItemLifecycle.Active && it.daysRemaining(today) in 0..30
    },
    expired = items.count {
        it.lifecycle == ItemLifecycle.Active && it.repeatRule == RepeatRule.None && it.daysRemaining(today) < 0
    },
    completed = items.count { it.lifecycle == ItemLifecycle.Completed }
)
