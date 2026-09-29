package com.example.despensadomestica.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Envuelve Firebase Authentication: registro, inicio de sesión, cierre de
 * sesión y recuperación de contraseña por correo electrónico.
 */
class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Colección minúscula solo para poder iniciar sesión con nombre de
    // usuario además de con correo: nombresUsuario/{nombreEnMinusculas} ->
    // { correo }. Firebase Authentication no permite iniciar sesión
    // directamente con el displayName, así que se necesita este pequeño
    // mapa para saber a qué correo corresponde el nombre ingresado.
    private fun claveUsuario(nombreUsuario: String) = nombreUsuario.trim().lowercase()

    /** Emite el usuario actual cada vez que cambia el estado de sesión. */
    val usuarioActual: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun usuarioActualInmediato(): FirebaseUser? = auth.currentUser

    /**
     * Crea la cuenta y guarda el nombre de usuario en el propio perfil de
     * Firebase Authentication (displayName), sin necesidad de una tabla o
     * colección aparte en la base de datos.
     */
    suspend fun registrarse(correo: String, contrasena: String, nombreUsuario: String): Result<Unit> {
        return try {
            val resultado = auth.createUserWithEmailAndPassword(correo, contrasena).await()
            resultado.user?.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(nombreUsuario).build()
            )?.await()
            // Se guarda la relación nombreUsuario -> correo para poder
            // iniciar sesión más adelante con cualquiera de los dos.
            firestore.collection("nombresUsuario")
                .document(claveUsuario(nombreUsuario))
                .set(mapOf("correo" to correo))
                .await()
            // Firebase inicia sesión automáticamente al crear la cuenta; se
            // cierra de inmediato para que el usuario inicie sesión de forma
            // explícita y no parezca que la app "salta" directo al formulario.
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inicia sesión con correo electrónico o con nombre de usuario: si lo
     * ingresado no tiene forma de correo, primero se busca a qué correo
     * corresponde ese nombre de usuario (colección nombresUsuario).
     */
    suspend fun iniciarSesion(usuarioOCorreo: String, contrasena: String): Result<Unit> {
        return try {
            val correo = if (usuarioOCorreo.contains("@")) {
                usuarioOCorreo
            } else {
                val documento = firestore.collection("nombresUsuario")
                    .document(claveUsuario(usuarioOCorreo))
                    .get()
                    .await()
                documento.getString("correo")
                    ?: return Result.failure(Exception("No existe ninguna cuenta con ese nombre de usuario"))
            }
            auth.signInWithEmailAndPassword(correo, contrasena).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Envía un correo de restablecimiento de contraseña. Por seguridad,
     * Firebase responde con éxito aunque el correo no esté registrado (para
     * no revelar qué correos existen en el sistema), así que el mensaje
     * mostrado al usuario debe ser genérico.
     */
    suspend fun restablecerContrasena(correo: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(correo).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cerrarSesion() {
        auth.signOut()
    }
}
