package com.example.notificadorrsuv5.data.local

import androidx.room.TypeConverter
import com.example.notificadorrsuv5.domain.model.ConditionOperator
import com.example.notificadorrsuv5.domain.model.FrequencyType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TypeConverters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.format(DateTimeFormatter.ISO_LOCAL_DATE)

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }

    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? = dateTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? = list?.joinToString(separator = "‚‗‚") // Unlikely separator

    @TypeConverter
    fun toStringList(value: String?): List<String>? = value?.split("‚‗‚")?.map { it.trim() }?.filter { it.isNotEmpty() }

    @TypeConverter
    fun fromConditionOperator(operator: ConditionOperator?): String? = operator?.name

    @TypeConverter
    fun toConditionOperator(value: String?): ConditionOperator? = value?.let { enumValueOf<ConditionOperator>(it) }

    @TypeConverter
    fun fromFrequencyType(frequency: FrequencyType?): String? = frequency?.name

    @TypeConverter
    fun toFrequencyType(value: String?): FrequencyType? = value?.let { enumValueOf<FrequencyType>(it) }
}