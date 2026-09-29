package com.example.despensadomestica.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.despensadomestica.ApiService
import com.example.despensadomestica.EliminarRequest
import com.example.despensadomestica.Producto
import com.example.despensadomestica.RetrofitInstance
import com.example.despensadomestica.data.local.AppDatabase
import com.example.despensadomestica.data.local.PreferenciasDataStore
import com.example.despensadomestica.data.local.ProductoEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Repositorio (patrón Repository): es la única puerta de entrada a los
 * datos para el ViewModel. Combina tres fuentes:
 *  - Local: Room (ProductoDao), fuente de verdad para la UI; permite que
 *    la app funcione sin conexión (offline-first).
 *  - Remota (API REST propia): API PHP/MySQL consumida con Retrofit,
 *    intercambiando JSON (ApiService). Es la evidencia de consumo de API
 *    REST que pide el rubro del curso.
 *  - Remota (Firebase): Cloud Firestore como respaldo en la nube ligado a
 *    la cuenta del usuario (Firebase Authentication). La foto del
 *    producto se guarda codificada en Base64 dentro del propio documento
 *    (no se usa Firebase Storage, para no depender del plan de pago
 *    Blaze).
 *
 * Estrategia offline-first para escritura: cada operación (registrar,
 * editar, eliminar) se guarda PRIMERO en Room, así que la app queda
 * usable de inmediato aunque no haya internet. Después se intenta
 * reflejar el cambio en la API propia y en Firestore; si Firestore falla
 * (sin conexión, por ejemplo), el producto queda marcado como pendiente
 * (sincronizadoFirestore = false) y sincronizarPendientesConFirestore()
 * lo vuelve a intentar automáticamente la próxima vez que haya conexión
 * (se llama cada vez que se recarga la lista de productos).
 *
 * Privacidad entre cuentas: Room es una sola base de datos compartida por
 * TODAS las cuentas que hayan iniciado sesión en este dispositivo. Cada
 * producto se guarda con el uid del usuario autenticado en ese momento
 * (propietarioUid) y todas las lecturas/escrituras locales se filtran por
 * ese uid, para que un usuario nunca vea ni pueda tocar los productos
 * guardados localmente por otra cuenta en el mismo celular. Este
 * Repository, además, se crea de nuevo cada vez que cambia el usuario con
 * sesión iniciada (ver la key en DespensaScreen), así que su uid no
 * queda "pegado" al de una sesión anterior.
 */
class DespensaRepository(context: Context) {

    private val contexto = context.applicationContext
    private val api: ApiService = RetrofitInstance.api
    private val dao = AppDatabase.getInstance(context).productoDao()
    private val preferencias = PreferenciasDataStore(context)

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /** uid del usuario con sesión iniciada al momento de crear este Repository. */
    private val uid: String? = auth.currentUser?.uid

    /**
     * Último error al hablar con Firestore (null si no hubo ninguno). El
     * ViewModel lo puede mostrar en pantalla; también queda en Logcat con
     * la etiqueta "DespensaRepository" (Logcat → filtrar por esa etiqueta).
     */
    var ultimoErrorFirestore: String? = null
        private set

    /**
     * Último error al hablar con la API REST propia (PHP/MySQL vía
     * Retrofit).
     */
    var ultimoErrorApi: String? = null
        private set

    /** Lista de productos (solo del usuario actual) observada desde la base de datos local (Room). */
    val productosLocales: Flow<List<Producto>> = if (uid != null) {
        dao.obtenerTodos(uid).map { lista -> lista.map { it.aProducto() } }
    } else {
        flowOf(emptyList())
    }

    /** Fecha/hora (epoch millis) de la última sincronización exitosa, desde DataStore. */
    val ultimaSincronizacion: Flow<Long> = preferencias.ultimaSincronizacion

    /** Referencia a la subcolección de productos del usuario autenticado, o null si no hay sesión. */
    private fun coleccionUsuario() = uid?.let { firestore.collection("usuarios").document(it).collection("productos") }

    /**
     * Trae la lista más reciente de la API REST propia (Retrofit) y
     * reemplaza en Room solo los productos del usuario actual que ya
     * tenían id real. Los productos creados sin conexión (todavía no
     * subidos) se conservan.
     */
    suspend fun sincronizarConServidor(): Result<Unit> {
        val uidActual = uid ?: return Result.failure(Exception("No hay sesión iniciada"))
        return try {
            val remotos = api.listarProductos(uidActual)
            dao.limpiarSincronizados(uidActual)
            dao.insertarTodos(remotos.map { it.aEntity(propietario = uidActual) })
            preferencias.guardarUltimaSincronizacion(System.currentTimeMillis())
            ultimoErrorApi = null
            Result.success(Unit)
        } catch (e: Exception) {
            ultimoErrorApi = "${e.javaClass.simpleName}: ${e.message}"
            Log.e("DespensaRepository", "Error al hablar con la API propia", e)
            Result.failure(e)
        }
    }

    /**
     * Recorre los productos del usuario actual que todavía no se han
     * subido a Cloud Firestore (los creados/editados sin conexión, y
     * también los que ya existían en Room desde antes de integrar
     * Firebase) e intenta subirlos ahora. Se debe llamar cada vez que hay
     * oportunidad de que ya haya conexión (por ejemplo, al abrir/recargar
     * la pantalla), para que el respaldo en la nube sea realmente
     * automático y no dependa de que el usuario vuelva a tocar
     * "Registrar" o "Actualizar".
     */
    suspend fun sincronizarPendientesConFirestore() {
        val uidActual = uid ?: return
        val pendientes = dao.obtenerPendientesDeFirestore(uidActual)
        for (entidad in pendientes) {
            respaldarEnFirestore(entidad.aProducto())
        }
    }

    /**
     * Comprime la foto elegida por el usuario (máx. 600px de lado, JPEG
     * calidad 70%) y la convierte en texto Base64 para guardarla como un
     * campo más del producto, sin necesidad de Firebase Storage. Corre en
     * un hilo de E/S porque lee el archivo desde el content resolver.
     */
    private suspend fun convertirImagenABase64(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val entrada = contexto.contentResolver.openInputStream(uri) ?: return@withContext null
            val bitmapOriginal = entrada.use { BitmapFactory.decodeStream(it) } ?: return@withContext null

            val ladoMaximo = 600
            val escala = minOf(1f, ladoMaximo.toFloat() / maxOf(bitmapOriginal.width, bitmapOriginal.height))
            val bitmapFinal = if (escala < 1f) {
                Bitmap.createScaledBitmap(
                    bitmapOriginal,
                    (bitmapOriginal.width * escala).toInt(),
                    (bitmapOriginal.height * escala).toInt(),
                    true
                )
            } else {
                bitmapOriginal
            }

            val salida = ByteArrayOutputStream()
            bitmapFinal.compress(Bitmap.CompressFormat.JPEG, 70, salida)
            Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Respalda (crea o actualiza) el producto en Cloud Firestore, bajo el
     * usuario autenticado. Si tiene éxito, marca el producto como
     * sincronizado en Room para que no se vuelva a intentar subir.
     *
     * @return true si el respaldo en Firestore tuvo éxito.
     */
    private suspend fun respaldarEnFirestore(producto: Producto): Boolean {
        val coleccion = coleccionUsuario()
        if (coleccion == null) {
            ultimoErrorFirestore = "No hay sesión de Firebase iniciada, no se respaldó en la nube"
            return false
        }
        return try {
            coleccion.document(producto.id.toString()).set(producto).await()
            dao.marcarSincronizadoFirestore(producto.id)
            ultimoErrorFirestore = null
            true
        } catch (e: Exception) {
            // Mejor esfuerzo: el producto ya está a salvo en Room y/o en la
            // API propia, pero dejamos rastro del error para poder
            // depurarlo (causa típica: sin conexión, o reglas de
            // seguridad de Firestore en modo producción que bloquean la
            // escritura). Queda con sincronizadoFirestore = false, así que
            // sincronizarPendientesConFirestore() lo reintentará luego.
            ultimoErrorFirestore = e.message
            Log.e("DespensaRepository", "Error al respaldar en Firestore", e)
            false
        }
    }

    /** Elimina el producto del respaldo en Cloud Firestore del usuario autenticado. */
    private suspend fun eliminarDeFirestore(id: Int) {
        val coleccion = coleccionUsuario() ?: return
        try {
            coleccion.document(id.toString()).delete().await()
            ultimoErrorFirestore = null
        } catch (e: Exception) {
            ultimoErrorFirestore = e.message
            Log.e("DespensaRepository", "Error al eliminar de Firestore", e)
        }
    }

    /** @return true si además quedó sincronizado con la API REST propia; false si solo se guardó localmente. */
    suspend fun registrarProducto(producto: Producto, imagenUri: Uri? = null): Boolean {
        val uidActual = uid ?: return false
        val idTemporal = generarIdLocalTemporal()
        val imagenBase64 = imagenUri?.let { convertirImagenABase64(it) }
        val productoLocal = producto.copy(id = idTemporal, imagenBase64 = imagenBase64)
        dao.insertar(productoLocal.aEntity(propietario = uidActual))

        val exito = try {
            api.agregarProducto(producto.copy(propietarioUid = uidActual))
            sincronizarConServidor()
            ultimoErrorApi = null
            true
        } catch (e: Exception) {
            ultimoErrorApi = "${e.javaClass.simpleName}: ${e.message}"
            Log.e("DespensaRepository", "Error al registrar en la API propia", e)
            false // sin conexión con la API propia: el producto queda guardado localmente
        }

        respaldarEnFirestore(productoLocal)
        return exito
    }

    suspend fun editarProducto(producto: Producto, imagenUri: Uri? = null): Boolean {
        val uidActual = uid ?: return false
        val imagenBase64 = imagenUri?.let { convertirImagenABase64(it) } ?: producto.imagenBase64
        val productoConImagen = producto.copy(imagenBase64 = imagenBase64)
        dao.insertar(productoConImagen.aEntity(propietario = uidActual))

        val exito = try {
            api.editarProducto(producto.copy(propietarioUid = uidActual))
            sincronizarConServidor()
            true
        } catch (e: Exception) {
            false // sin conexión con la API propia: la edición queda guardada localmente
        }

        respaldarEnFirestore(productoConImagen)
        return exito
    }

    suspend fun eliminarProducto(id: Int): Boolean {
        val uidActual = uid ?: return false
        dao.eliminarPorId(id, uidActual)
        eliminarDeFirestore(id)
        return try {
            api.eliminarProducto(EliminarRequest(id, uidActual))
            true
        } catch (e: Exception) {
            false // sin conexión con la API propia: se eliminó localmente y en Firestore
        }
    }

    /** Genera un id negativo (nunca choca con los ids reales, positivos, que asigna el servidor). */
    private fun generarIdLocalTemporal(): Int =
        -((System.currentTimeMillis() % 1_000_000_000L).toInt().let { if (it <= 0) it - 1 else it })
}

private fun Producto.aEntity(propietario: String, sincronizado: Boolean = false) = ProductoEntity(
    id = id,
    nombre = nombre,
    categoria = categoria,
    cantidad = cantidad,
    fechaVencimiento = fecha_vencimiento,
    estado = estado,
    imagenBase64 = imagenBase64,
    sincronizadoFirestore = sincronizado,
    propietarioUid = propietario
)

private fun ProductoEntity.aProducto() = Producto(
    id = id,
    nombre = nombre,
    categoria = categoria,
    cantidad = cantidad,
    fecha_vencimiento = fechaVencimiento,
    estado = estado,
    imagenBase64 = imagenBase64
)
