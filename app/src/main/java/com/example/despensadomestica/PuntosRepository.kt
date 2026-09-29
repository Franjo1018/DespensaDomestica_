package com.example.despensadomestica

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repositorio de los puntos de referencia del mapa, guardados en Cloud
 * Firestore. No hay copia local: el mapa siempre necesita conexión para
 * mostrarse (a diferencia de la despensa, que sí funciona sin conexión
 * gracias a Room). Maneja dos colecciones distintas:
 *
 *  - Privada (puntos de tienda, buenos precios): usuarios/{uid}/puntosReferencia/{id}.
 *    Solo el dueño la puede leer o escribir.
 *  - Global (puntos de donación): puntosDonacion/{id}, en la raíz de
 *    Firestore. Cualquier usuario autenticado la puede leer (para que
 *    todos vean los mismos puntos) y crear documentos nuevos; solo quien
 *    creó un punto (campo creadoPor) puede borrarlo.
 *
 * IMPORTANTE: esto requiere que las reglas de seguridad de Firestore
 * permitan lectura a cualquier usuario autenticado en "puntosDonacion" y
 * escritura solo cuando creadoPor == request.auth.uid (y borrado igual).
 * Si el mapa no muestra los puntos de donación de otros usuarios, revisa
 * primero esas reglas en la consola de Firebase.
 */
class PuntosRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun coleccionPrivada() = auth.currentUser?.uid?.let { uid ->
        firestore.collection("usuarios").document(uid).collection("puntosReferencia")
    }

    private fun coleccionDonaciones() = firestore.collection("puntosDonacion")

    /** uid del usuario actual, o null si no hay sesión. Útil para saber si el usuario puede borrar un punto de donación. */
    val uidActual: String? get() = auth.currentUser?.uid

    /** Puntos de tienda (privados) del usuario actual. */
    suspend fun obtenerPuntos(): List<PuntoReferencia> {
        val coleccion = coleccionPrivada() ?: return emptyList()
        return try {
            coleccion.get().await().documents.mapNotNull { documento ->
                documento.toObject(PuntoReferencia::class.java)?.copy(id = documento.id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Puntos de donación (globales): los ven todos los usuarios de la app. */
    suspend fun obtenerPuntosDonacion(): List<PuntoReferencia> {
        return try {
            coleccionDonaciones().get().await().documents.mapNotNull { documento ->
                documento.toObject(PuntoReferencia::class.java)?.copy(id = documento.id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun guardarPunto(nombre: String, descripcion: String, latitud: Double, longitud: Double): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("No hay sesión iniciada"))
        val coleccion = coleccionPrivada() ?: return Result.failure(Exception("No hay sesión iniciada"))
        return try {
            val punto = PuntoReferencia(
                nombre = nombre,
                descripcion = descripcion,
                latitud = latitud,
                longitud = longitud,
                tipo = TipoPunto.TIENDA,
                creadoPor = uid
            )
            coleccion.add(punto).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Crea un punto de donación GLOBAL: lo van a ver todos los usuarios de la app. */
    suspend fun guardarPuntoDonacion(nombre: String, descripcion: String, latitud: Double, longitud: Double): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("No hay sesión iniciada"))
        return try {
            val punto = PuntoReferencia(
                nombre = nombre,
                descripcion = descripcion,
                latitud = latitud,
                longitud = longitud,
                tipo = TipoPunto.DONACION,
                creadoPor = uid
            )
            coleccionDonaciones().add(punto).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun eliminarPunto(id: String): Result<Unit> {
        val coleccion = coleccionPrivada() ?: return Result.failure(Exception("No hay sesión iniciada"))
        return try {
            coleccion.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Borra un punto de donación global. Solo debería llamarse cuando el
     * punto le pertenece al usuario actual (creadoPor == uidActual); la
     * pantalla ya se encarga de no mostrar el botón de borrar en los
     * puntos de otras personas, pero la verdadera protección debe estar
     * en las reglas de seguridad de Firestore.
     */
    suspend fun eliminarPuntoDonacion(id: String): Result<Unit> {
        if (auth.currentUser == null) return Result.failure(Exception("No hay sesión iniciada"))
        return try {
            coleccionDonaciones().document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
