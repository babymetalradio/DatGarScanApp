package com.datgarscan.app.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.datgarscan.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Diálogo de actualización in-app:
 * - Título "Nueva versión disponible"
 * - Notas de la versión
 * - Barra + porcentaje de descarga
 * - Instala el APK al terminar
 */
object ActualizacionHelper {

    /**
     * Muestra el diálogo. Si [obligatorio] es true, no se puede cerrar sin actualizar.
     */
    fun mostrar(
        activity: Activity,
        versionName: String,
        notas: String?,
        apkUrl: String?,
        obligatorio: Boolean,
        onMasTarde: (() -> Unit)? = null
    ) {
        if (activity.isFinishing) return
        val url = apkUrl?.trim().orEmpty()
        if (url.isEmpty()) {
            Toast.makeText(activity, "No hay enlace de descarga.", Toast.LENGTH_SHORT).show()
            return
        }

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_actualizacion, null)
        val tvTitulo = view.findViewById<TextView>(R.id.tvUpdateTitulo)
        val tvVersion = view.findViewById<TextView>(R.id.tvUpdateVersion)
        val tvNotas = view.findViewById<TextView>(R.id.tvUpdateNotas)
        val progress = view.findViewById<ProgressBar>(R.id.progressUpdate)
        val tvPorcentaje = view.findViewById<TextView>(R.id.tvUpdatePorcentaje)
        val btnActualizar = view.findViewById<Button>(R.id.btnUpdateAhora)
        val btnMasTarde = view.findViewById<Button>(R.id.btnUpdateMasTarde)

        tvTitulo.text = "Hay una nueva actualización"
        tvVersion.text = "Versión $versionName"
        val textoNotas = notas?.trim().orEmpty()
        if (textoNotas.isNotEmpty()) {
            tvNotas.visibility = View.VISIBLE
            tvNotas.text = textoNotas
        } else {
            tvNotas.visibility = View.GONE
        }
        progress.visibility = View.GONE
        tvPorcentaje.visibility = View.GONE
        progress.progress = 0
        tvPorcentaje.text = "0%"

        if (obligatorio) {
            btnMasTarde.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(!obligatorio)
            .create()

        btnMasTarde.setOnClickListener {
            onMasTarde?.invoke()
            dialog.dismiss()
        }

        btnActualizar.setOnClickListener {
            btnActualizar.isEnabled = false
            btnMasTarde.isEnabled = false
            progress.visibility = View.VISIBLE
            tvPorcentaje.visibility = View.VISIBLE
            progress.isIndeterminate = false
            progress.max = 100
            progress.progress = 0
            tvPorcentaje.text = "0%"
            btnActualizar.text = "Descargando…"

            CoroutineScope(Dispatchers.Main).launch {
                val resultado = withContext(Dispatchers.IO) {
                    descargarApk(activity, url) { pct ->
                        activity.runOnUiThread {
                            progress.progress = pct
                            tvPorcentaje.text = "$pct%"
                        }
                    }
                }
                if (activity.isFinishing) return@launch
                resultado.fold(
                    onSuccess = { file ->
                        progress.progress = 100
                        tvPorcentaje.text = "100%"
                        btnActualizar.text = "Instalar"
                        btnActualizar.isEnabled = true
                        btnActualizar.setOnClickListener {
                            instalarApk(activity, file)
                        }
                        // Intento de instalar de inmediato
                        instalarApk(activity, file)
                        if (!obligatorio) dialog.dismiss()
                    },
                    onFailure = { e ->
                        btnActualizar.isEnabled = true
                        btnMasTarde.isEnabled = !obligatorio
                        btnActualizar.text = "Reintentar"
                        Toast.makeText(
                            activity,
                            "Error al descargar: ${e.message ?: "desconocido"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }

        dialog.show()
    }

    private fun descargarApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> {
        return try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(dir, "datgarscan-update.apk")
            if (outFile.exists()) outFile.delete()

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 120_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                return Result.failure(Exception("HTTP ${conn.responseCode}"))
            }
            val total = conn.contentLengthLong.coerceAtLeast(0L)
            conn.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var leido = 0L
                    var leidoUltimoPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        leido += n
                        if (total > 0) {
                            val pct = ((leido * 100) / total).toInt().coerceIn(0, 100)
                            if (pct != leidoUltimoPct) {
                                leidoUltimoPct = pct
                                onProgress(pct)
                            }
                        } else {
                            // Sin Content-Length: simula avance suave hasta 90%
                            val fake = (leido / (512 * 1024)).toInt().coerceIn(0, 90)
                            if (fake != leidoUltimoPct) {
                                leidoUltimoPct = fake
                                onProgress(fake)
                            }
                        }
                    }
                    output.flush()
                }
            }
            onProgress(100)
            if (!outFile.exists() || outFile.length() < 1000) {
                return Result.failure(Exception("Archivo incompleto"))
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun instalarApk(activity: Activity, apk: File) {
        try {
            // Android 8+: permitir instalar desde esta fuente
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                    Toast.makeText(
                        activity,
                        "Activa «Instalar apps desconocidas» y vuelve a pulsar Actualizar.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "No se pudo instalar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
