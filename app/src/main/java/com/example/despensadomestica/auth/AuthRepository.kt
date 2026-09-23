package com.example.despensadomestica.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Envuelve Firebase Authentication (correo y contraseña). Expone el
 * usuario actual como Flow para que la UI pueda reaccionar a inicio y
 * cierre de sesión automáticamente (patrón Repository, igual que
 * DespensaRepository para los datos).
 */
class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    /** Usuario autenticado actualmente, o null si no hay sesión iniciada. */
    val usuarioActual: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun usuarioActualInmediato(): FirebaseUser? = auth.currentUser

    suspend fun registrarse(correo: String, contrasena: String): Result<Unit> {
        return try {
            auth.createUserWithEmailAndPassword(correo, contrasena).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun iniciarSesion(correo: String, contrasena: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(correo, contrasena).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cerrarSesion() {
        auth.signOut()
    }
}
