package com.example.despensadomestica

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.despensadomestica.data.local.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private const val ID_CANAL_VENCIMIENTOS = "vencimientos_despensa"
private const val NOMBRE_TRABAJO_PERIODICO = "revisar_vencimientos_periodico"
private const val NOMBRE_TRABAJO_INMEDIATO = "revisar_vencimientos_inmediato"

/**
 * Crea (o actualiza, si ya existía) el canal de notificaciones de
 * vencimientos. Hay que llamarlo una vez al iniciar la app (se hace en
 * MainActivity), antes de que se pueda mostrar cualquier notificación:
 * es obligatorio desde Android 8 (API 26) y este proyecto ya tiene
 * minSdk 26, así que no hace falta comprobar la versión del sistema.
 */
fun crearCanalNotificacionesVencimiento(contexto: Context) {
    val canal = NotificationChannel(
        ID_CANAL_VENCIMIENTOS,
        "Vencimiento de alimentos",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Avisa cuando un producto de tu despensa está por vencer"
    }
    val administrador = contexto.getSystemService(NotificationManager::class.java)
    administrador?.createNotificationChannel(canal)
}

/**
 * Programa con WorkManager la revisión diaria de vencimientos: sigue
 * funcionando aunque la app esté cerrada (WorkManager la despierta en
 * segundo plano). También se lanza, aparte, una revisión inmediata (una
 * sola vez) para que si ya hay productos por vencer el aviso no tenga
 * que esperar hasta el día siguiente. Se debe llamar una vez al iniciar
 * la app (en MainActivity), después de pedir el permiso de
 * notificaciones.
 */
fun programarRevisionDeVencimientos(contexto: Context) {
    val trabajoPeriodico = PeriodicWorkRequestBuilder<VencimientoWorker>(24, TimeUnit.HOURS).build()
    WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(
        NOMBRE_TRABAJO_PERIODICO,
        ExistingPeriodicWorkPolicy.KEEP,
        trabajoPeriodico
    )

    val trabajoInmediato = OneTimeWorkRequestBuilder<VencimientoWorker>().build()
    WorkManager.getInstance(contexto).enqueueUniqueWork(
        NOMBRE_TRABAJO_INMEDIATO,
        ExistingWorkPolicy.KEEP,
        trabajoInmediato
    )
}

/**
 * Revisa todos los productos guardados en Room y notifica dos veces por
 * producto:
 *  - Cuando faltan entre 4 y 7 días para que venza ("una semana antes").
 *  - Cuando faltan entre 0 y 3 días, o ya venció ("está por vencer").
 *
 * Cada aviso se manda una sola vez por producto (se marca en Room con
 * notificado7Dias/notificado3Dias), aunque el chequeo corra todos los
 * días mientras el producto siga en ese rango.
 */
class VencimientoWorker(contexto: Context, parametros: WorkerParameters) :
    CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        // Solo se revisan los productos del usuario que tiene la sesión
        // iniciada en este momento (Room guarda los productos de todas
        // las cuentas que hayan usado este dispositivo, así que hay que
        // filtrar por uid para no mezclar ni notificar productos de otra
        // cuenta).
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        val dao = AppDatabase.getInstance(applicationContext).productoDao()
        val hoy = LocalDate.now()

        val productos = try {
            dao.obtenerTodosUnaVez(uid)
        } catch (e: Exception) {
            return Result.retry()
        }

        for (producto in productos) {
            val fechaVencimiento = try {
                LocalDate.parse(producto.fechaVencimiento) // formato guardado: "yyyy-MM-dd"
            } catch (e: DateTimeParseException) {
                continue // fecha con formato inesperado o vacía: no se puede calcular, se ignora
            }

            val diasRestantes = ChronoUnit.DAYS.between(hoy, fechaVencimiento)

            if (diasRestantes in 4..7 && !producto.notificado7Dias) {
                mostrarNotificacion(
                    idNotificacion = producto.id * 10 + 1,
                    titulo = "Vence pronto: ${producto.nombre}",
                    texto = "Te queda aproximadamente una semana para consumir \"${producto.nombre}\"."
                )
                dao.marcarNotificado7Dias(producto.id)
            }

            if (diasRestantes in 0..3 && !producto.notificado3Dias) {
                val cuando = if (diasRestantes == 0L) "hoy" else "en $diasRestantes día(s)"
                mostrarNotificacion(
                    idNotificacion = producto.id * 10 + 2,
                    titulo = "¡Está por vencer!: ${producto.nombre}",
                    texto = "\"${producto.nombre}\" vence $cuando. Úsalo pronto para no desperdiciarlo."
                )
                dao.marcarNotificado3Dias(producto.id)
            }
        }

        return Result.success()
    }

    private fun mostrarNotificacion(idNotificacion: Int, titulo: String, texto: String) {
        val contexto = applicationContext

        // Desde Android 13 (API 33) mostrar una notificación requiere
        // permiso explícito del usuario (POST_NOTIFICATIONS). Se pide al
        // abrir la app (junto con cámara y ubicación); si el usuario
        // todavía no lo concedió, simplemente no se muestra el aviso (no
        // se puede volver a pedir el permiso desde un Worker en segundo
        // plano).
        if (ActivityCompat.checkSelfPermission(
                contexto,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificacion = NotificationCompat.Builder(contexto, ID_CANAL_VENCIMIENTOS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(contexto).notify(idNotificacion, notificacion)
        } catch (e: SecurityException) {
            // Falta el permiso pese al chequeo anterior (caso muy raro de
            // que lo revoquen justo en este instante); se ignora.
        }
    }
}
