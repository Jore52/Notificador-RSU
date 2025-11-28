package com.example.notificadorrsuv5.domain.model

data class MemberModel(
    val id: String = "", // Usamos String para compatibilidad general (aunque Room use Long internamente para IDs autogenerados, en dominio es mejor ser flexibles o mapear)
    val fullName: String = "",
    val role: String = "",
    val dni: String = "",
    val phone: String = "",
    val email: String = ""
)