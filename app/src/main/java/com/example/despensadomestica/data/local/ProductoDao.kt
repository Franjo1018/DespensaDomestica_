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
 */
@Dao
interface ProductoDao {

    @Query("SELECT * FROM productos ORDER BY fechaVencimiento ASC")
    fun obtenerTodos(): Flow<List<ProductoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(producto: ProductoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(productos: List<ProductoEntity>)

    @Delete
    suspend fun eliminar(producto: ProductoEntity)

    @Query("DELETE FROM productos WHERE id = :id")
    suspend fun eliminarPorId(id: Int)

    @Query("DELETE FROM productos")
    suspend fun limpiarTodo()

    /**
     * Borra solo los productos con id >= 0 (es decir, los que ya vinieron
     * del servidor alguna vez). Los productos creados sin conexión quedan
     * con un id temporal negativo y NO se tocan aquí, para no perderlos
     * en una sincronización mientras esperan a subirse al servidor.
     */
    @Query("DELETE FROM productos WHERE id >= 0")
    suspend fun limpiarSincronizados()
}
