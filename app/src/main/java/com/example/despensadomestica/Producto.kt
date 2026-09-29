package com.example.despensadomestica

data class Producto(
    val id: Int = 0,
    val nombre: String,
    val categoria: String,
    val cantidad: Int,
    val fecha_vencimiento: String,
    val estado: String? = "Disponible",
    val imagenBase64: String? = null,
    /**
     * uid (Firebase Authentication) del dueño del producto. Se envía a la
     * API propia (PHP/MySQL) para que cada cuenta solo pueda ver y tocar
     * sus propios productos ahí también (columna firebase_uid en MySQL),
     * igual que ya pasa en Room y en Firestore. DespensaRepository lo
     * completa justo antes de mandar el producto al servidor; al leer
     * productos que YA vienen del servidor (listar_productos.php no
     * devuelve esta columna) simplemente se queda en "".
     */
    val propietarioUid: String = ""
)

data class EliminarRequest(val id: Int, val uid: String = "")
