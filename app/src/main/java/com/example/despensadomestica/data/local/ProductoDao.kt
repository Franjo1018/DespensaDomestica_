package com.example.despensadomestica.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) de Room para la tabla "productos".
 * Expone los datos locales como Flow para que la UI se actualice
 * automáticamente cuando cambia la caché local.
 *
 * IMPORTANTE: la base de datos es una sola por dispositivo, compartida
 * entre todas las cuentas que inicien sesión en ese celular. Por eso casi
 * todas las consultas reciben un [uid] (el del usuario con la sesión
 * iniciada en ese momento) y filtran por la columna propietarioUid, para
 * que cada usuario vea únicamente sus propios productos.
 */
@Dao
interface ProductoDao {

    @Query("SELECT * FROM productos WHERE propietarioUid = :uid ORDER BY fechaVencimiento ASC")
    fun obtenerTodos(uid: String): Flow<List<ProductoEntity>>

    /** Igual que obtenerTodos(), pero de una sola vez (no observa cambios). Usado por el chequeo de vencimientos, que corre en segundo plano. */
    @Query("SELECT * FROM productos WHERE propietarioUid = :uid")
    suspend fun obtenerTodosUnaVez(uid: String): List<ProductoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(producto: ProductoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(productos: List<ProductoEntity>)

    @Delete
    suspend fun eliminar(producto: ProductoEntity)

    /** El filtro por uid evita que un usuario pueda borrar, ni por accidente, un producto que no es suyo. */
    @Query("DELETE FROM productos WHERE id = :id AND propietarioUid = :uid")
    suspend fun eliminarPorId(id: Int, uid: String)

    @Query("DELETE FROM productos")
    suspend fun limpiarTodo()

    /**
     * Borra solo los productos de este usuario con id >= 0 (es decir, los
     * que ya vinieron del servidor alguna vez). Los productos creados sin
     * conexión quedan con un id temporal negativo y NO se tocan aquí, para
     * no perderlos en una sincronización mientras esperan a subirse al
     * servidor. Se filtra por uid para no tocar los productos guardados
     * localmente de otras cuentas que hayan usado este mismo dispositivo.
     */
    @Query("DELETE FROM productos WHERE id >= 0 AND propietarioUid = :uid")
    suspend fun limpiarSincronizados(uid: String)

    /**
     * Productos de este usuario que todavía no se han subido a Cloud
     * Firestore (por ejemplo: se crearon sin conexión, o ya existían en
     * Room desde antes de integrar Firebase). Se usan para reintentar el
     * respaldo cuando vuelve la conexión.
     */
    @Query("SELECT * FROM productos WHERE propietarioUid = :uid AND sincronizadoFirestore = 0")
    suspend fun obtenerPendientesDeFirestore(uid: String): List<ProductoEntity>

    @Query("UPDATE productos SET sincronizadoFirestore = 1 WHERE id = :id")
    suspend fun marcarSincronizadoFirestore(id: Int)

    @Query("UPDATE productos SET notificado7Dias = 1 WHERE id = :id")
    suspend fun marcarNotificado7Dias(id: Int)

    @Query("UPDATE productos SET notificado3Dias = 1 WHERE id = :id")
    suspend fun marcarNotificado3Dias(id: Int)
}
