package com.datgarscan.app.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.datgarscan.app.MainActivity
import com.datgarscan.app.R

/**
 * Reproduce el stream de BMRadio1 en segundo plano.
 * Stream: Zeno.fm https://stream.zeno.fm/fvqayaa0k2zuv
 */
class RadioPlayerService : Service() {

    companion object {
        const val ACTION_PLAY = "com.datgarscan.app.radio.PLAY"
        const val ACTION_PAUSE = "com.datgarscan.app.radio.PAUSE"
        const val ACTION_TOGGLE = "com.datgarscan.app.radio.TOGGLE"
        const val ACTION_STOP = "com.datgarscan.app.radio.STOP"
        const val ACTION_STATE = "com.datgarscan.app.radio.STATE"

        const val EXTRA_PLAYING = "playing"

        private const val STREAM_URL = "https://stream.zeno.fm/fvqayaa0k2zuv"
        private const val CANAL = "bmradio1"
        private const val NOTIF_ID = 91001

        @Volatile var isPlaying: Boolean = false
            private set

        fun toggle(context: Context) {
            val i = Intent(context, RadioPlayerService::class.java).setAction(ACTION_TOGGLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RadioPlayerService::class.java).setAction(ACTION_STOP))
        }
    }

    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY, null -> play()
            ACTION_PAUSE -> pause()
            ACTION_TOGGLE -> if (isPlaying) pause() else play()
            ACTION_STOP -> {
                pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun play() {
        try {
            if (player == null) {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(STREAM_URL)
                    setOnPreparedListener {
                        start()
                        isPlaying = true
                        startForeground(NOTIF_ID, buildNotification(true))
                        emitState()
                    }
                    setOnErrorListener { _, _, _ ->
                        isPlaying = false
                        emitState()
                        releasePlayer()
                        true
                    }
                    setOnCompletionListener {
                        // streams en vivo no suelen completar; por si acaso
                        isPlaying = false
                        emitState()
                    }
                    prepareAsync()
                }
                startForeground(NOTIF_ID, buildNotification(false))
            } else {
                player?.start()
                isPlaying = true
                startForeground(NOTIF_ID, buildNotification(true))
                emitState()
            }
        } catch (e: Exception) {
            isPlaying = false
            emitState()
            releasePlayer()
        }
    }

    private fun pause() {
        try {
            player?.pause()
        } catch (_: Exception) { }
        isPlaying = false
        emitState()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(false))
    }

    private fun releasePlayer() {
        try {
            player?.reset()
            player?.release()
        } catch (_: Exception) { }
        player = null
        isPlaying = false
    }

    private fun emitState() {
        sendBroadcast(Intent(ACTION_STATE).putExtra(EXTRA_PLAYING, isPlaying).setPackage(packageName))
    }

    private fun buildNotification(playing: Boolean): Notification {
        crearCanal()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val togglePi = PendingIntent.getService(
            this, 1,
            Intent(this, RadioPlayerService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 2,
            Intent(this, RadioPlayerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val titulo = if (playing) "BMRadio1 · En vivo" else "BMRadio1 · Pausado"
        val accion = if (playing) "Pausar" else "Reproducir"
        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText("Toca para volver a Dat-Gar Scan")
            .setContentIntent(openApp)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .addAction(0, accion, togglePi)
            .addAction(0, "Detener", stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "BMRadio1", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Reproductor de radio en vivo"
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
}
