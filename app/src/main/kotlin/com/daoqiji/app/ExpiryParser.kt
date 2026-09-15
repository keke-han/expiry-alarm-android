package com.daoqiji.app

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ExpiryParser {
    private const val NUMBER_CHARACTERS = "零〇一二两三四五六七八九十百千壹贰叁肆伍陆柒捌玖拾佰仟"
    private const val NUMBER_PATTERN = "[0-9$NUMBER_CHARACTERS]+"
    private val isoDateRegex = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val chineseDateRegex = Regex("""($NUMBER_PATTERN)年($NUMBER_PATTERN)月($NUMBER_PATTERN)[日号]""")
    private val isoMonthRegex = Regex("""(\d{4})-(\d{1,2})(?!-\d)""")
    private val chineseMonthRegex = Regex("""($NUMBER_PATTERN)年($NUMBER_PATTERN)月(?:份)?""")
    private val reminderSectionRegex = Regex("""提前\s*(.+?)(?:提醒|$)""")
    private val reminderValueRegex = Regex("""半年|($NUMBER_PATTERN)\s*(年|个月|月|周|星期|天|日)""")
    private val yearlyRepeatRegex = Regex("""每年(?:重复)?""")
    private val dateCueRegex = Regex(
        """(?:保质期|有效期|到期日期|到期日|到期|截止日期|截止日|截止)\s*(?:到|至)?"""
    )

    fun parse(input: String, defaultRemindBeforeDays: Int = 30): ParsedExpiry? {
        val fullDateMatch = isoDateRegex.find(input) ?: chineseDateRegex.find(input)
        val monthMatch = if (fullDateMatch == null) {
            isoMonthRegex.find(input) ?: chineseMonthRegex.find(input)
        } else {
            null
        }
        val dateMatch = fullDateMatch ?: monthMatch ?: return null
        val expireDate = if (fullDateMatch != null) {
            fullDateMatch.toDateOrNull()
        } else {
            monthMatch?.toMonthEndDateOrNull()
        } ?: return null
        val reminderMatch = reminderSectionRegex.find(input)
        val reminderDays = reminderMatch?.groupValues?.get(1)
            ?.let { value -> reminderValueRegex.findAll(value).map { it.toReminderDays() }.toList() }
            .orEmpty()
        val remindBeforeDays = reminderDays.firstOrNull() ?: defaultRemindBeforeDays
        val repeatRule = if (yearlyRepeatRegex.containsMatchIn(input)) RepeatRule.Yearly else RepeatRule.None
        val title = input
            .replace(dateMatch.value, "")
            .replace(reminderMatch?.value.orEmpty(), "")
            .replace(yearlyRepeatRegex, "")
            .replace(dateCueRegex, "")
            .replace("提醒", "")
            .replace(Regex("""[，,。；;：:]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

        if (title.isBlank()) return null
        return ParsedExpiry(
            title = title,
            expireDate = expireDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            remindBeforeDays = remindBeforeDays,
            repeatRule = repeatRule,
            additionalRemindBeforeDays = reminderDays.drop(1).filter { it != remindBeforeDays }.distinct()
        )
    }

    private fun MatchResult.toDateOrNull(): LocalDate? {
        val year = groupValues[1].toChineseNumberOrNull() ?: return null
        val month = groupValues[2].toChineseNumberOrNull() ?: return null
        val day = groupValues[3].toChineseNumberOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun MatchResult.toMonthEndDateOrNull(): LocalDate? {
        val year = groupValues[1].toChineseNumberOrNull() ?: return null
        val month = groupValues[2].toChineseNumberOrNull() ?: return null
        return runCatching {
            LocalDate.of(year, month, 1).withDayOfMonth(
                LocalDate.of(year, month, 1).lengthOfMonth()
            )
        }.getOrNull()
    }

    private fun MatchResult.toReminderDays(): Int {
        if (value == "半年") return 180
        val amount = groupValues[1].toChineseNumberOrNull() ?: return 0
        return when (groupValues[2]) {
            "年" -> amount * 365
            "个月", "月" -> amount * 30
            "周", "星期" -> amount * 7
            else -> amount
        }
    }

    private fun String.toChineseNumberOrNull(): Int? {
        toIntOrNull()?.let { return it }

        val digitValues = mapOf(
            '零' to 0, '〇' to 0,
            '一' to 1, '壹' to 1,
            '二' to 2, '两' to 2, '贰' to 2,
            '三' to 3, '叁' to 3,
            '四' to 4, '肆' to 4,
            '五' to 5, '伍' to 5,
            '六' to 6, '陆' to 6,
            '七' to 7, '柒' to 7,
            '八' to 8, '捌' to 8,
            '九' to 9, '玖' to 9
        )
        val unitValues = mapOf(
            '十' to 10, '拾' to 10,
            '百' to 100, '佰' to 100,
            '千' to 1_000, '仟' to 1_000
        )

        if (none { it in unitValues }) {
            return map { digitValues[it] ?: return null }
                .joinToString("")
                .toIntOrNull()
        }

        var total = 0
        var currentDigit = 0
        for (character in this) {
            digitValues[character]?.let {
                currentDigit = it
                continue
            }
            val unit = unitValues[character] ?: return null
            total += (if (currentDigit == 0) 1 else currentDigit) * unit
            currentDigit = 0
        }
        return total + currentDigit
    }
}
