package com.example.despensadomestica

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.despensadomestica.data.DespensaRepository
import kotlinx.coroutines.launch

/**
 * ViewModel (MVVM): habla con DespensaRepository, que decide si los datos
 * vienen de Cloud Firestore o de la caché local en Room (incluida la foto
 * comprimida en Base64). Ya no depende de ningún servidor PHP/MySQL
 * propio. Al heredar de AndroidViewModel obtenemos el Context de la
 * aplicación, necesario para inicializar Room y DataStore.
 */
class DespensaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DespensaRepository(application)

    var productos = mutableStateOf<List<Producto>>(emptyList())
    var mensajeEstado = mutableStateOf("")
    var productoEditandoId by mutableStateOf<Int?>(null)

    init {
        // La pantalla observa siempre la base de datos local (Room):
        // así se sigue viendo la despensa aunque no haya conexión.
        viewModelScope.launch {
            repository.productosLocales.collect { lista ->
                productos.value = lista
            }
        }
        cargarProductos()
    }

    fun cargarProductos() {
        viewModelScope.launch {
            val resultado = repository.sincronizarConServidor()
            mensajeEstado.value = if (resultado.isSuccess) {
                ""
            } else {
                "Sin conexión con la API: ${repository.ultimoErrorApi ?: "mostrando datos guardados localmente"}"
            }
        }
    }

    fun registrarProducto(nombre: String, categoria: String, cantidad: String, fecha: String, imagenUri: Uri? = null) {
        viewModelScope.launch {
            try {
                val nuevo = Producto(
                    nombre = nombre,
                    categoria = categoria,
                    cantidad = cantidad.toIntOrNull() ?: 1,
                    fecha_vencimiento = fecha
                )
                val sincronizado = repository.registrarProducto(nuevo, imagenUri)
                val base = if (sincronizado) "Registrado" else "Guardado localmente (sin conexión)"
                val errorNube = repository.ultimoErrorFirestore
                mensajeEstado.value = if (errorNube != null) "$base. Firebase: $errorNube" else base
            } catch (e: Exception) {
                mensajeEstado.value = "Error al guardar: ${e.message}"
            }
        }
    }

    fun editarProducto(id: Int, nombre: String, categoria: String, cantidad: String, fecha: String, imagenUri: Uri? = null) {
        viewModelScope.launch {
            try {
                val actual = productos.value.firstOrNull { it.id == id }
                val actualizado = Producto(id, nombre, categoria, cantidad.toIntOrNull() ?: 1, fecha, imagenBase64 = actual?.imagenBase64)
                val sincronizado = repository.editarProducto(actualizado, imagenUri)
                val base = if (sincronizado) "Actualizado" else "Actualizado localmente (sin conexión)"
                val errorNube = repository.ultimoErrorFirestore
                mensajeEstado.value = if (errorNube != null) "$base. Firebase: $errorNube" else base
                productoEditandoId = null
            } catch (e: Exception) {
                mensajeEstado.value = "Error al editar: ${e.message}"
            }
        }
    }

    fun eliminarProducto(id: Int) {
        viewModelScope.launch {
            try {
                val sincronizado = repository.eliminarProducto(id)
                mensajeEstado.value = if (sincronizado) "Eliminado" else "Eliminado localmente (sin conexión)"
            } catch (e: Exception) {
                mensajeEstado.value = "Error al eliminar: ${e.message}"
            }
        }
    }
}
