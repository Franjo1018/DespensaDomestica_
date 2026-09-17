package com.example.despensadomestica

data class Producto(
    val id: Int = 0,
    val nombre: String,
    val categoria: String,
    val cantidad: Int,
    val fecha_vencimiento: String,
    val estado: String? = "Disponible"
)

data class EliminarRequest(val id: Int)