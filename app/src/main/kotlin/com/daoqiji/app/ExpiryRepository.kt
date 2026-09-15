package com.daoqiji.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class ExpiryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("expiry_items", Context.MODE_PRIVATE)

    fun getAll(): List<ExpiryItem> {
        val raw = prefs.getString(KEY_ITEMS, "[]").orEmpty()
        val items = JSONArray(raw)
        return buildList {
            for (index in 0 until items.length()) {
                val json = items.optJSONObject(index) ?: continue
                val title = json.optString("title")
                add(
                    ExpiryItem(
                        id = json.optString("id"),
                        title = title,
                        expireDate = json.optString("expire_date"),
                        remindBeforeDays = json.optInt("remind_before_days", 30),
                        createdAt = json.optString("created_at"),
                        repeatRule = RepeatRule.entries.firstOrNull {
                            it.name == json.optString("repeat_rule")
                        } ?: RepeatRule.None,
                        category = ItemCategory.entries.firstOrNull {
                            it.name == json.optString("category")
                        } ?: inferCategory(title),
                        additionalRemindBeforeDays = json.optJSONArray("additional_remind_before_days")
                            ?.toIntList().orEmpty(),
                        lifecycle = ItemLifecycle.entries.firstOrNull {
                            it.name == json.optString("lifecycle")
                        } ?: ItemLifecycle.Active,
                        renewalHistory = json.optJSONArray("renewal_history")?.toStringList().orEmpty()
                    )
                )
            }
        }.filter { it.id.isNotBlank() && it.title.isNotBlank() && it.expireDate.isNotBlank() }
            .sortedBy { it.expireDate }
    }

    fun add(parsed: ParsedExpiry): ExpiryItem {
        val item = ExpiryItem(
            id = UUID.randomUUID().toString(),
            title = parsed.title,
            expireDate = parsed.expireDate,
            remindBeforeDays = parsed.remindBeforeDays,
            createdAt = LocalDate.now().toString(),
            repeatRule = parsed.repeatRule,
            category = inferCategory(parsed.title),
            additionalRemindBeforeDays = parsed.additionalRemindBeforeDays
        )
        saveAll(getAll() + item)
        return item
    }

    fun update(item: ExpiryItem) {
        saveAll(getAll().map { if (it.id == item.id) item else it })
    }

    fun delete(id: String) {
        saveAll(getAll().filterNot { it.id == id })
    }

    fun exportBackup(): String = ExpiryBackup.encode(getAll())

    fun importBackup(raw: String): Int {
        val restored = ExpiryBackup.decode(raw)
        saveAll(restored)
        return restored.size
    }

    private fun saveAll(items: List<ExpiryItem>) {
        val array = JSONArray()
        items.sortedBy { it.expireDate }.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("expire_date", item.expireDate)
                    .put("remind_before_days", item.remindBeforeDays)
                    .put("created_at", item.createdAt)
                    .put("repeat_rule", item.repeatRule.name)
                    .put("category", item.category.name)
                    .put("additional_remind_before_days", JSONArray(item.additionalRemindBeforeDays))
                    .put("lifecycle", item.lifecycle.name)
                    .put("renewal_history", JSONArray(item.renewalHistory))
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun JSONArray.toIntList(): List<Int> =
        (0 until length()).mapNotNull { index -> optInt(index).takeIf { it >= 0 } }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { index -> optString(index).takeIf(String::isNotBlank) }

    private companion object {
        const val KEY_ITEMS = "items"
    }
}
