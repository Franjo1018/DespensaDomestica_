package com.example.despensadomestica.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.auth.FirebaseAuth

/**
 * Base de datos local (Room).
 *
 * Versión 4: se agregó sincronizadoFirestore (ver ProductoEntity), para
 * saber qué productos ya se subieron a Cloud Firestore y cuáles siguen
 * pendientes.
 *
 * Versión 5: se agregaron notificado7Dias y notificado3Dias, para poder
 * avisarle al usuario cuando un producto está por vencer (una semana
 * antes, y 3 días antes o menos) sin repetir el mismo aviso cada día.
 *
 * Versión 6: se agregó propietarioUid, para que cada producto quede
 * ligado al usuario (Firebase Authentication) que lo creó. Antes de este
 * cambio, la base de datos era una sola compartida por TODOS los usuarios
 * que iniciaran sesión en el mismo celular, así que cualquier cuenta
 * nueva veía también los productos de las demás cuentas de ese
 * dispositivo. La migración deja los productos que ya estaban guardados
 * (de antes de este cambio) asignados a quien tenga la sesión iniciada en
 * el momento de abrir la app por primera vez después de actualizar,
 * porque normalmente son los de esa misma cuenta.
 *
 * Todas estas migraciones son reales (no fallbackToDestructiveMigration)
 * para no perder los productos que el usuario ya tenía guardados
 * localmente al actualizar el esquema.
 */
@Database(entities = [ProductoEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productoDao(): ProductoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Por defecto 0 (false): así, todo lo que ya estaba
                // guardado en Room antes de este cambio (incluidos los
                // productos "viejos" creados antes de integrar Firebase)
                // queda marcado como pendiente, y la próxima vez que la
                // app tenga conexión se sube automáticamente a Firestore.
                db.execSQL(
                    "ALTER TABLE productos ADD COLUMN sincronizadoFirestore INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRACION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE productos ADD COLUMN notificado7Dias INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE productos ADD COLUMN notificado3Dias INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRACION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE productos ADD COLUMN propietarioUid TEXT NOT NULL DEFAULT ''"
                )
                // Los productos guardados ANTES de este cambio no tenían
                // dueño: se le asignan a quien tenga la sesión iniciada en
                // este momento (normalmente es la misma cuenta que ya los
                // había creado). Si nadie tiene sesión iniciada todavía,
                // se quedan sin dueño (propietarioUid = "") y por lo tanto
                // no se le mostrarán a ninguna cuenta hasta que alguien
                // vuelva a sincronizar.
                val uidActual = FirebaseAuth.getInstance().currentUser?.uid
                if (uidActual != null) {
                    db.execSQL(
                        "UPDATE productos SET propietarioUid = ? WHERE propietarioUid = ''",
                        arrayOf(uidActual)
                    )
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instancia = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "despensa_domestica_db"
                )
                    .addMigrations(MIGRACION_3_4, MIGRACION_4_5, MIGRACION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instancia
                instancia
            }
        }
    }
}
