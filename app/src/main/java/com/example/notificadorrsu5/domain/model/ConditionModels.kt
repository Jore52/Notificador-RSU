package com.example.notificadorrsuv5.domain.model

enum class ConditionOperator(val symbol: String) {
    EQUAL_TO("="),
    LESS_THAN("<"),
    GREATER_THAN(">"),
    LESS_THAN_OR_EQUAL_TO("≤"),
    GREATER_THAN_OR_EQUAL_TO("≥")
}

enum class FrequencyType(val description: String) {
    ONCE("Una sola vez"),

    DAILY("Diariamente (si la condición se mantiene)")
}