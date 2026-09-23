package com.example.despensadomestica

data class Producto(
    val id: Int = 0,
    val nombre: String,
    val categoria: String,
    val cantidad: Int,
    val fecha_vencimiento: String,
    val estado: String? = "Disponible",
    /** Foto del producto codificada en Base64 (JPEG comprimido), o null si no tiene. */
    val imagenBase64: String? = null
)

data class EliminarRequest(val id: Int)
