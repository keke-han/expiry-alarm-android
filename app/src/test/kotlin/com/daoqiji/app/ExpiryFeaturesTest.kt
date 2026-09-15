package com.daoqiji.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpiryFeaturesTest {
    private val items = listOf(
        ExpiryItem("1", "护照", "2026-10-20", 30, "2026-09-01"),
        ExpiryItem("2", "牛奶", "2026-09-15", 3, "2026-09-01"),
        ExpiryItem("3", "会员", "2026-08-01", 7, "2026-09-01")
    )

    @Test
    fun sensitivePermissionRequiresPrivacyConsent() {
        assertFalse(canRequestSensitivePermission(false))
        assertTrue(canRequestSensitivePermission(true))
    }

    @Test
    fun filtersByKeywordAndStatus() {
        val today = LocalDate.of(2026, 9, 10)

        assertEquals(listOf("护照"), filterExpiryItems(items, "护", ExpiryFilter.All, today).map { it.title })
        assertEquals(listOf("牛奶"), filterExpiryItems(items, "", ExpiryFilter.Within30Days, today).map { it.title })
        assertEquals(listOf("会员"), filterExpiryItems(items, "", ExpiryFilter.Expired, today).map { it.title })
    }

    @Test
    fun groupsItemsIntoCalendarMonths() {
        val groups = groupExpiryItemsByMonth(items)

        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), groups.keys.toList())
        assertEquals("牛奶", groups[YearMonth.of(2026, 9)]!!.single().title)
    }

    @Test
    fun calendarGroupsYearlyItemsByTheirNextOccurrence() {
        val birthday = ExpiryItem("4", "妈妈生日", "2020-05-20", 7, "2020-01-01", RepeatRule.Yearly)

        val groups = groupExpiryItemsByMonth(listOf(birthday), LocalDate.of(2026, 6, 1))

        assertEquals(listOf(YearMonth.of(2027, 5)), groups.keys.toList())
    }

    @Test
    fun yearlyReminderUsesTheNextOccurrence() {
        val birthday = ExpiryItem("4", "妈妈生日", "2020-05-20", 7, "2020-01-01", RepeatRule.Yearly)

        assertEquals(LocalDate.of(2027, 5, 20), birthday.nextOccurrence(LocalDate.of(2026, 6, 1)))
        assertEquals(LocalDate.of(2026, 5, 20), birthday.nextOccurrence(LocalDate.of(2026, 5, 20)))
        assertEquals(LocalDate.of(2026, 5, 13), birthday.reminderDate(LocalDate.of(2026, 5, 13)))
    }

    @Test
    fun infersUsefulCategoriesFromCommonTitles() {
        assertEquals(ItemCategory.Document, inferCategory("护照"))
        assertEquals(ItemCategory.Card, inferCategory("工商银行信用卡"))
        assertEquals(ItemCategory.Membership, inferCategory("Netflix会员"))
        assertEquals(ItemCategory.Food, inferCategory("酸奶保质期"))
        assertEquals(ItemCategory.Anniversary, inferCategory("妈妈生日"))
    }

    @Test
    fun supportsMultipleReminderDatesWithoutDuplicates() {
        val item = ExpiryItem(
            "4", "护照", "2026-12-31", 30, "2026-09-01",
            additionalRemindBeforeDays = listOf(7, 1, 30)
        )

        assertEquals(listOf(30, 7, 1), item.allReminderDays())
        assertTrue(item.shouldNotifyOn(LocalDate.of(2026, 12, 1)))
        assertTrue(item.shouldNotifyOn(LocalDate.of(2026, 12, 24)))
        assertFalse(item.shouldNotifyOn(LocalDate.of(2026, 12, 20)))
        assertEquals(7, item.leadDaysOn(LocalDate.of(2026, 12, 24)))
        assertEquals(null, item.leadDaysOn(LocalDate.of(2026, 12, 20)))
    }

    @Test
    fun completedItemsHaveTheirOwnFilterAndOverviewCount() {
        val completed = items[0].copy(lifecycle = ItemLifecycle.Completed)
        val source = listOf(completed, items[1], items[2])
        val today = LocalDate.of(2026, 9, 10)

        assertEquals(listOf("护照"), filterExpiryItems(source, "", ExpiryFilter.Completed, today).map { it.title })
        assertEquals(listOf("牛奶"), filterExpiryItems(source, "", ExpiryFilter.Within30Days, today).map { it.title })
        assertEquals(1, expiryOverview(source, today).completed)
        assertEquals(1, expiryOverview(source, today).expired)
    }

    @Test
    fun renewingAnItemKeepsHistoryAndReactivatesIt() {
        val completed = items[0].copy(lifecycle = ItemLifecycle.Completed)

        val renewed = completed.renew("2030-10-20")

        assertEquals("2030-10-20", renewed.expireDate)
        assertEquals(ItemLifecycle.Active, renewed.lifecycle)
        assertEquals(listOf("2026-10-20"), renewed.renewalHistory)
    }

    @Test
    fun backupRoundTripPreservesExpiryManagementFields() {
        val original = ExpiryItem(
            id = "backup-1",
            title = "护照",
            expireDate = "2030-10-20",
            remindBeforeDays = 30,
            createdAt = "2026-09-13",
            category = ItemCategory.Document,
            additionalRemindBeforeDays = listOf(7, 1),
            lifecycle = ItemLifecycle.Completed,
            renewalHistory = listOf("2026-10-20")
        )

        val restored = ExpiryBackup.decode(ExpiryBackup.encode(listOf(original)))

        assertEquals(listOf(original), restored)
    }
}
