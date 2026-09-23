package com.example.despensadomestica.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

/**
 * ViewModel de autenticación (MVVM). MainActivity observa `usuario` para
 * decidir si muestra LoginScreen o DespensaScreen.
 */
class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    var usuario by mutableStateOf<FirebaseUser?>(repository.usuarioActualInmediato())
        private set

    var cargando by mutableStateOf(false)
        private set

    var mensajeError by mutableStateOf<String?>(null)
        private set

    var mensajeExito by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            repository.usuarioActual.collect { usuario = it }
        }
    }

    fun registrarse(correo: String, contrasena: String) {
        mensajeError = null
        mensajeExito = null
        viewModelScope.launch {
            cargando = true
            val resultado = repository.registrarse(correo, contrasena)
            cargando = false
            if (resultado.isFailure) {
                mensajeError = resultado.exceptionOrNull()?.message ?: "No se pudo registrar la cuenta"
            } else {
                mensajeExito = "Cuenta creada. Ahora inicia sesión con tu correo y contraseña."
            }
        }
    }

    fun iniciarSesion(correo: String, contrasena: String) {
        mensajeError = null
        mensajeExito = null
        viewModelScope.launch {
            cargando = true
            val resultado = repository.iniciarSesion(correo, contrasena)
            cargando = false
            if (resultado.isFailure) {
                mensajeError = resultado.exceptionOrNull()?.message ?: "Correo o contraseña incorrectos"
            }
        }
    }

    fun cerrarSesion() {
        repository.cerrarSesion()
    }
}
