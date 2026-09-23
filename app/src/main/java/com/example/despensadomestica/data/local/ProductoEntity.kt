package com.example.despensadomestica.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de Room: representa la tabla "productos" en la base de datos
 * local (SQLite). imagenBase64 guarda la foto del producto ya comprimida
 * y codificada en Base64 (no se usa Firebase Storage: así se evita
 * depender del plan de pago Blaze), de modo que la miniatura se siga
 * viendo aunque el dispositivo esté sin conexión.
 */
@Entity(tableName = "productos")
data class ProductoEntity(
    @PrimaryKey val id: Int,
    val nombre: String,
    val categoria: String,
    val cantidad: Int,
    val fechaVencimiento: String,
    val estado: String? = "Disponible",
    val imagenBase64: String? = null
)
