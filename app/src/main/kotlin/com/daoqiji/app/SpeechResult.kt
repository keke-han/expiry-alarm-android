package com.daoqiji.app

internal fun usableSpeechResult(text: String): String? =
    text.trim().takeIf { it.isNotEmpty() && "<unk>" !in it }
