package com.example.despensadomestica.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de Room: representa la tabla "productos" en la base de datos
 * local (SQLite). imagenBase64 guarda la foto del producto ya comprimida
 * y codificada en Base64 (no se usa Firebase Storage: así se evita
 * depender del plan de pago Blaze), de modo que la miniatura se siga
 * viendo aunque el dispositivo esté sin conexión.
 *
 * sincronizadoFirestore indica si este producto ya se subió a Cloud
 * Firestore. Empieza en false para todo producto nuevo; cuando el
 * respaldo en Firestore tiene éxito se marca en true. Sirve para que,
 * al recuperar la conexión, la app pueda recorrer solo los productos
 * pendientes y subirlos (offline-first real), en vez de depender de que
 * el usuario vuelva a tocar "Registrar"/"Actualizar".
 *
 * notificado7Dias / notificado3Dias marcan si ya se le avisó al usuario
 * que este producto está por vencer (una semana antes, y 3 días antes o
 * menos). Sirven para que VencimientoWorker no mande el mismo aviso más
 * de una vez, aunque el chequeo corra todos los días.
 *
 * propietarioUid guarda el uid (Firebase Authentication) del usuario
 * dueño de este producto. La base de datos Room es UNA SOLA por
 * dispositivo, compartida entre todas las cuentas que hayan iniciado
 * sesión en ese celular: sin este campo, cualquier usuario que inicie
 * sesión ahí vería también los productos de los demás. Todas las
 * consultas del DAO filtran por este campo con el uid de quien tiene la
 * sesión iniciada en ese momento.
 */
@Entity(tableName = "productos")
data class ProductoEntity(
    @PrimaryKey val id: Int,
    val nombre: String,
    val categoria: String,
    val cantidad: Int,
    val fechaVencimiento: String,
    val estado: String? = "Disponible",
    val imagenBase64: String? = null,
    val sincronizadoFirestore: Boolean = false,
    val notificado7Dias: Boolean = false,
    val notificado3Dias: Boolean = false,
    val propietarioUid: String = ""
)
