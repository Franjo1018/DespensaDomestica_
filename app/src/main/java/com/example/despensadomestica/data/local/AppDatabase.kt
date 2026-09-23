package com.example.despensadomestica.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local (Room). Versión 3: el campo de foto del producto
 * pasó de "imagenUrl" (Firebase Storage) a "imagenBase64" (la foto se
 * guarda codificada dentro del propio documento/registro, sin depender
 * de un bucket de almacenamiento). Como el proyecto usa
 * fallbackToDestructiveMigration(), Room recrea la tabla local
 * automáticamente al detectar el cambio de esquema; no se pierde nada
 * importante porque la caché local siempre puede volver a llenarse con
 * sincronizarConServidor().
 */
@Database(entities = [ProductoEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productoDao(): ProductoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instancia = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "despensa_domestica_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instancia
                instancia
            }
        }
    }
}
