package com.example.despensadomestica

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * ViewModel (MVVM) del mapa: habla con PuntosRepository (Cloud Firestore).
 * Mantiene dos listas separadas:
 *  - [puntos]: puntos de tienda (buenos precios), privados del usuario.
 *  - [puntosDonacion]: puntos de donación, globales para todos los usuarios.
 */
class MapaViewModel : ViewModel() {

    private val repository = PuntosRepository()

    var puntos = mutableStateOf<List<PuntoReferencia>>(emptyList())
        private set

    var puntosDonacion = mutableStateOf<List<PuntoReferencia>>(emptyList())
        private set

    var mensajeError by mutableStateOf<String?>(null)
        private set

    /** uid del usuario actual: sirve para saber si puede borrar un punto de donación (solo su propio creador puede). */
    val uidActual: String? get() = repository.uidActual

    init {
        cargarPuntos()
    }

    fun cargarPuntos() {
        viewModelScope.launch {
            puntos.value = repository.obtenerPuntos()
            puntosDonacion.value = repository.obtenerPuntosDonacion()
        }
    }

    /**
     * Guarda un nuevo punto. Si [esDonacion] es true, se guarda en la
     * colección global de donaciones (lo ven todos los usuarios); si es
     * false, se guarda como punto de tienda privado (solo lo ve quien lo
     * creó).
     */
    fun guardarPunto(nombre: String, descripcion: String, latitud: Double, longitud: Double, esDonacion: Boolean) {
        viewModelScope.launch {
            val resultado = if (esDonacion) {
                repository.guardarPuntoDonacion(nombre, descripcion, latitud, longitud)
            } else {
                repository.guardarPunto(nombre, descripcion, latitud, longitud)
            }
            if (resultado.isFailure) {
                mensajeError = resultado.exceptionOrNull()?.message
            } else {
                cargarPuntos()
            }
        }
    }

    /** [esDonacion] indica de qué colección borrar (debe coincidir con el tipo del punto). */
    fun eliminarPunto(id: String, esDonacion: Boolean) {
        viewModelScope.launch {
            if (esDonacion) {
                repository.eliminarPuntoDonacion(id)
            } else {
                repository.eliminarPunto(id)
            }
            cargarPuntos()
        }
    }
}
