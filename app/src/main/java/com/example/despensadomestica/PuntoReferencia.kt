package com.example.despensadomestica

/**
 * Punto de referencia guardado en el mapa. Puede ser de dos tipos
 * (campo [tipo]):
 *  - "TIENDA": un lugar (mercado, tienda, feria) donde el usuario
 *    encontró productos de despensa a buen precio. Es privado: solo lo ve
 *    el usuario que lo guardó (se guarda en su propia subcolección de
 *    Firestore).
 *  - "DONACION": un lugar para donar insumos (banco de alimentos, comedor,
 *    parroquia, etc.). Es global: lo puede crear cualquier usuario y lo
 *    ven TODOS los usuarios de la app (se guarda en una colección
 *    compartida de Firestore, no dentro de un usuario en particular).
 *
 * [creadoPor] guarda el uid del usuario que creó el punto; se usa sobre
 * todo en los puntos de donación (globales) para saber quién puede
 * borrarlos. Los documentos antiguos, guardados antes de que existiera
 * este campo, se siguen leyendo bien: Firestore usa el valor por defecto
 * ("TIENDA") cuando el campo no existe en el documento.
 *
 * Todos los campos tienen valor por defecto para que Firestore pueda
 * reconstruir el objeto por reflexión (toObject) al leer un documento.
 */
data class PuntoReferencia(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val tipo: String = "TIENDA",
    val creadoPor: String = ""
)

object TipoPunto {
    const val TIENDA = "TIENDA"
    const val DONACION = "DONACION"
}
