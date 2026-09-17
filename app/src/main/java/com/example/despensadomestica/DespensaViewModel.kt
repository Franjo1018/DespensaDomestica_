package com.example.despensadomestica

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

class DespensaViewModel : ViewModel() {
    var productos = mutableStateOf<List<Producto>>(emptyList())
    var mensajeEstado = mutableStateOf("")
    var productoEditandoId by mutableStateOf<Int?>(null)
    fun cargarProductos() {
        viewModelScope.launch {
            try {
                productos.value = RetrofitInstance.api.listarProductos()
            } catch (e: Exception) {
                mensajeEstado.value = "Error al conectar: ${e.message}"
            }
        }
    }

    fun registrarProducto(nombre: String, categoria: String, cantidad: String, fecha: String) {
        viewModelScope.launch {
            try {
                val nuevo = Producto(
                    nombre = nombre,
                    categoria = categoria,
                    cantidad = cantidad.toIntOrNull() ?: 1,
                    fecha_vencimiento = fecha
                )
                val respuesta = RetrofitInstance.api.agregarProducto(nuevo)
                mensajeEstado.value = respuesta["mensaje"] ?: "Registrado"
                cargarProductos()
            } catch (e: Exception) {
                mensajeEstado.value = "Error al guardar: ${e.message}"
            }
        }
    }



    fun editarProducto(id: Int, nombre: String, categoria: String, cantidad: String, fecha: String) {
        viewModelScope.launch {
            try {
                val actualizado = Producto(id, nombre, categoria, cantidad.toIntOrNull() ?: 1, fecha)
                val respuesta = RetrofitInstance.api.editarProducto(actualizado)
                mensajeEstado.value = respuesta["mensaje"] ?: "Actualizado"
                productoEditandoId = null
                cargarProductos()
            } catch (e: Exception) {
                mensajeEstado.value = "Error al editar: ${e.message}"
            }
        }
    }

    fun eliminarProducto(id: Int) {
        viewModelScope.launch {
            try {
                val respuesta = RetrofitInstance.api.eliminarProducto(EliminarRequest(id))
                mensajeEstado.value = respuesta["mensaje"] ?: "Eliminado"
                cargarProductos()
            } catch (e: Exception) {
                mensajeEstado.value = "Error al eliminar: ${e.message}"
            }
        }
    }
}