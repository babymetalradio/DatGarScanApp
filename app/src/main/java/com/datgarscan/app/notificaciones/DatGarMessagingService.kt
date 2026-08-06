package com.datgarscan.app.notificaciones

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.datgarscan.app.MainActivity
import com.datgarscan.app.R
import com.datgarscan.app.webapi.SesionManager
import com.datgarscan.app.webapi.TokenRequest
import com.datgarscan.app.webapi.WebApiClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val CANAL_NOTIFICACIONES = "capitulos_nuevos"

class DatGarMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        NotificacionesManager.enviarTokenAlServidor(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val titulo = message.data["titulo"] ?: message.notification?.title ?: "Nuevo capítulo"
        val cuerpo = message.data["cuerpo"] ?: message.notification?.body ?: ""
        val slug = message.data["manga_slug"]

        // Siempre entra por MainActivity, nunca directo a la pantalla del manga:
        // MainActivity es quien sabe resolver el desafío anti-bot del hosting
        // antes de pedir nada a la API. Si abriéramos SerieDetalleActivity
        // directo (con la app cerrada), esa pantalla pediría datos antes de
        // que la conexión esté lista y fallaría.
        val intent = Intent(this, MainActivity::class.java)
        if (!slug.isNullOrBlank()) {
            intent.putExtra(MainActivity.EXTRA_ABRIR_MANGA_SLUG, slug)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        crearCanalSiHaceFalta(this)

        val notificacion = NotificationCompat.Builder(this, CANAL_NOTIFICACIONES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notificacion)
    }
}

fun crearCanalSiHaceFalta(context: Context) {
    try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val canal = NotificationChannel(
            CANAL_NOTIFICACIONES,
            "Capítulos nuevos",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        canal.description = "Avisos cuando sale un capítulo nuevo de tus series favoritas"
        manager.createNotificationChannel(canal)
    }
    } catch (e: Throwable) {
        android.util.Log.w("Notificaciones", "No se pudo crear el canal de notificaciones")
    }
}

object NotificacionesManager {

    fun registrarSiHaySesion(context: Context) {
        if (!SesionManager.estaLogueado()) return

        // Envuelto en try/catch porque hay dispositivos sin Google Play Services
        // (reproductores de audio, tablets chinas, telefonos con ROMs sin Google)
        // donde Firebase no existe. En esos casos la app debe seguir funcionando
        // normal, solo sin notificaciones.
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> enviarTokenAlServidor(context, token) }
                .addOnFailureListener { /* sin servicios de Google, seguimos igual */ }
        } catch (e: Throwable) {
            android.util.Log.w("Notificaciones", "Firebase no disponible en este dispositivo")
        }
    }

    fun enviarTokenAlServidor(context: Context, token: String) {
        if (!SesionManager.estaLogueado()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WebApiClient.get().registrarTokenNotificaciones(TokenRequest(token))
            } catch (e: Exception) {
                // si falla, se reintenta la proxima vez que se abra la app
            }
        }
    }
}
