package com.datgarscan.app.ads

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Banner propio desde la web (/api/banner_propio.php).
 * Si "forzar" está activo en el panel, la app salta Unity y muestra solo este.
 */
object BannerPropioHelper {

    private const val TAG = "BannerPropio"
    private const val TAG_VIEW = "banner_propio"
    private const val API_URL = "https://datgarscanlation.xyz/api/banner_propio.php"

    @Volatile private var cacheActivo = false
    @Volatile private var cacheForzar = false
    @Volatile private var cacheImagen: String? = null
    @Volatile private var cacheLink: String? = null
    @Volatile private var cacheVersion = -1
    @Volatile private var ultimaCargaMs = 0L

    /** true si el panel pide forzar solo banner propio. */
    fun debeForzar(): Boolean {
        refrescarCacheSiHaceFalta()
        return cacheForzar && cacheActivo && !cacheImagen.isNullOrBlank()
    }

    fun mostrar(activity: Activity, container: ViewGroup) {
        if (activity.isFinishing) return
        if (activity is com.datgarscan.app.tienda.TiendaActivity) return
        try {
            if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(activity)) {
                container.visibility = android.view.View.GONE
                return
            }
        } catch (_: Throwable) { }

        for (i in 0 until container.childCount) {
            if (container.getChildAt(i).tag == TAG_VIEW) return
        }

        Thread {
            refrescarCacheSiHaceFalta()
            if (!cacheActivo || cacheImagen.isNullOrBlank()) {
                Log.d(TAG, "inactivo o sin imagen")
                return@Thread
            }
            val urlImg = cacheImagen!!
            val link = cacheLink
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                for (i in 0 until container.childCount) {
                    if (container.getChildAt(i).tag == TAG_VIEW) return@runOnUiThread
                }
                val density = activity.resources.displayMetrics.density
                val iv = ImageView(activity).apply {
                    tag = TAG_VIEW
                    scaleType = ImageView.ScaleType.FIT_XY
                    contentDescription = "Anuncio"
                    layoutParams = FrameLayout.LayoutParams(
                        (320 * density).toInt(),
                        (50 * density).toInt(),
                        android.view.Gravity.CENTER_HORIZONTAL
                    )
                    setOnClickListener { abrirDestino(activity, link) }
                }
                try {
                    Glide.with(activity).load(urlImg).into(iv)
                } catch (e: Exception) {
                    Log.e(TAG, "Glide", e)
                    return@runOnUiThread
                }
                container.addView(iv, 0)
                container.visibility = android.view.View.VISIBLE
            }
        }.start()
    }

    fun quitar(container: ViewGroup) {
        for (i in container.childCount - 1 downTo 0) {
            if (container.getChildAt(i).tag == TAG_VIEW) container.removeViewAt(i)
        }
    }

    private fun refrescarCacheSiHaceFalta() {
        val ahora = System.currentTimeMillis()
        // Con forzar, refrescar más seguido (1 min); normal 5 min
        val ttl = if (cacheForzar) 60_000L else 5 * 60_000L
        if (ahora - ultimaCargaMs < ttl && cacheVersion >= 0) return
        try {
            val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            conn.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                cacheActivo = json.optBoolean("activo", false)
                cacheForzar = json.optBoolean("forzar", false)
                cacheImagen = json.optString("imagen_url", null).takeIf { !it.isNullOrBlank() && it != "null" }
                cacheLink = json.optString("link_url", null).takeIf { !it.isNullOrBlank() && it != "null" }
                cacheVersion = json.optInt("version", 0)
                ultimaCargaMs = ahora
                Log.d(TAG, "v=$cacheVersion activo=$cacheActivo forzar=$cacheForzar")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "error API: ${e.message}")
        }
    }

    private fun abrirDestino(activity: Activity, link: String?) {
        try {
            if (!link.isNullOrBlank()) {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            } else {
                activity.startActivity(com.datgarscan.app.tienda.TiendaActivity.crearIntent(activity))
            }
        } catch (_: Throwable) {
            try {
                activity.startActivity(Intent(activity, com.datgarscan.app.tienda.TiendaActivity::class.java))
            } catch (_: Throwable) { }
        }
    }
}
