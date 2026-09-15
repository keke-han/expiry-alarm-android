package com.daoqiji.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object ExpiryBackup {
    private const val SCHEMA_VERSION = 1

    fun encode(items: List<ExpiryItem>): String = JSONObject()
        .put("schema_version", SCHEMA_VERSION)
        .put("exported_at", LocalDate.now().toString())
        .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        .toString(2)

    fun decode(raw: String): List<ExpiryItem> {
        val root = JSONObject(raw)
        require(root.optInt("schema_version") == SCHEMA_VERSION) { "不支持的备份版本" }
        val array = root.getJSONArray("items")
        return (0 until array.length()).map { index -> array.getJSONObject(index).toExpiryItem() }
    }

    internal fun ExpiryItem.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("expire_date", expireDate)
        .put("remind_before_days", remindBeforeDays)
        .put("created_at", createdAt)
        .put("repeat_rule", repeatRule.name)
        .put("category", category.name)
        .put("additional_remind_before_days", JSONArray(additionalRemindBeforeDays))
        .put("lifecycle", lifecycle.name)
        .put("renewal_history", JSONArray(renewalHistory))

    internal fun JSONObject.toExpiryItem(): ExpiryItem {
        val title = getString("title")
        val date = getString("expire_date")
        LocalDate.parse(date)
        return ExpiryItem(
            id = getString("id"),
            title = title,
            expireDate = date,
            remindBeforeDays = optInt("remind_before_days", 30),
            createdAt = optString("created_at", LocalDate.now().toString()),
            repeatRule = RepeatRule.entries.firstOrNull { it.name == optString("repeat_rule") }
                ?: RepeatRule.None,
            category = ItemCategory.entries.firstOrNull { it.name == optString("category") }
                ?: inferCategory(title),
            additionalRemindBeforeDays = optJSONArray("additional_remind_before_days").toIntList(),
            lifecycle = ItemLifecycle.entries.firstOrNull { it.name == optString("lifecycle") }
                ?: ItemLifecycle.Active,
            renewalHistory = optJSONArray("renewal_history").toStringList()
        )
    }

    private fun JSONArray?.toIntList(): List<Int> = this?.let { array ->
        (0 until array.length()).mapNotNull { index -> array.optInt(index).takeIf { it >= 0 } }
    }.orEmpty()

    private fun JSONArray?.toStringList(): List<String> = this?.let { array ->
        (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
    }.orEmpty()
}
