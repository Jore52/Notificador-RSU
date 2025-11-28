package com.example.notificadorrsuv5.domain.model

enum class ConditionOperator(val symbol: String) {
    EQUAL_TO("="),
    LESS_THAN("<"),
    GREATER_THAN(">"),
    LESS_THAN_OR_EQUAL_TO("≤"),
    GREATER_THAN_OR_EQUAL_TO("≥");

    // Método auxiliar para mostrar texto legible en la UI
    fun toReadableString(): String = when (this) {
        EQUAL_TO -> "Igual a"
        LESS_THAN -> "Menor que"
        GREATER_THAN -> "Mayor que"
        LESS_THAN_OR_EQUAL_TO -> "Menor o igual que"
        GREATER_THAN_OR_EQUAL_TO -> "Mayor o igual que"
    }
}

enum class FrequencyType(val description: String) {
    ONCE("Una sola vez"),
    DAILY("Diariamente (si la condición se mantiene)");

    fun toReadableString(): String = this.description
}

// ESTA ES LA CLASE QUE FALTABA:
data class ConditionModel(
    val id: String = "", // String para compatibilidad con UUID/Firebase
    val name: String = "",
    val subject: String = "",
    val body: String = "",
    val deadlineDays: Int = 0,
    val operator: ConditionOperator = ConditionOperator.EQUAL_TO,
    val frequency: FrequencyType = FrequencyType.ONCE,
    val attachmentUris: List<String> = emptyList()
)