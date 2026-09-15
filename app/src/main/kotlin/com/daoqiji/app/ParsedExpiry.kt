package com.daoqiji.app

data class ParsedExpiry @JvmOverloads constructor(
    val title: String,
    val expireDate: String,
    val remindBeforeDays: Int,
    val repeatRule: RepeatRule = RepeatRule.None,
    val additionalRemindBeforeDays: List<Int> = emptyList()
)
