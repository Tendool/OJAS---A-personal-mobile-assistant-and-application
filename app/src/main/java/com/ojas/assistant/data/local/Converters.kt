package com.ojas.assistant.data.local

import androidx.room.TypeConverter
import com.ojas.assistant.data.local.entity.RepeatRule

class Converters {
    @TypeConverter
    fun toRepeat(value: String?): RepeatRule =
        value?.let { runCatching { RepeatRule.valueOf(it) }.getOrNull() } ?: RepeatRule.NONE

    @TypeConverter
    fun fromRepeat(rule: RepeatRule): String = rule.name
}
