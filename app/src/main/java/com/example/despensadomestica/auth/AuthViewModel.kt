package com.example.despensadomestica.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

/**
 * ViewModel (MVVM) de autenticación: expone el usuario actual y delega en
 * AuthRepository las operaciones de registro, inicio de sesión, cierre de
 * sesión y recuperación de contraseña.
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
            repository.usuarioActual.collect { usuarioActual ->
                usuario = usuarioActual
            }
        }
    }

    fun registrarse(correo: String, contrasena: String, nombreUsuario: String) {
        viewModelScope.launch {
            cargando = true
            mensajeError = null
            mensajeExito = null
            val resultado = repository.registrarse(correo, contrasena, nombreUsuario)
            cargando = false
            if (resultado.isSuccess) {
                mensajeExito = "Cuenta creada. Ahora inicia sesión con tu correo y contraseña."
            } else {
                mensajeError = resultado.exceptionOrNull()?.message ?: "No se pudo crear la cuenta"
            }
        }
    }

    fun iniciarSesion(usuarioOCorreo: String, contrasena: String) {
        viewModelScope.launch {
            cargando = true
            mensajeError = null
            mensajeExito = null
            val resultado = repository.iniciarSesion(usuarioOCorreo, contrasena)
            cargando = false
            if (resultado.isFailure) {
                mensajeError = resultado.exceptionOrNull()?.message ?: "No se pudo iniciar sesión"
            }
        }
    }

    /** Envía el correo de recuperación de contraseña y muestra un mensaje genérico. */
    fun restablecerContrasena(correo: String) {
        viewModelScope.launch {
            cargando = true
            mensajeError = null
            mensajeExito = null
            val resultado = repository.restablecerContrasena(correo)
            cargando = false
            if (resultado.isSuccess) {
                mensajeExito = "Si el correo está registrado, te enviamos un enlace para restablecer tu contraseña."
            } else {
                mensajeError = resultado.exceptionOrNull()?.message ?: "No se pudo enviar el correo de recuperación"
            }
        }
    }

    fun cerrarSesion() {
        repository.cerrarSesion()
    }
}
