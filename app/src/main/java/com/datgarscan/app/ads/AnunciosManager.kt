package com.datgarscan.app.ads

import android.content.Context
import android.view.View
import com.startapp.sdk.adsbase.StartAppAd

/**
 * Banners e intersticiales: StartApp.
 * Rewarded (garritas): Unity Ads (ver UnityAdsManager / TiendaActivity).
 */
object AnunciosManager {

    private const val PREFS = "datgar_ads"
    private const val KEY_CONTADOR = "capitulos_abiertos"
    private const val KEY_CONTADOR_SALIDA = "salidas_lector"
    private const val CADA_CUANTOS_CAPITULOS = 1
    private const val CADA_CUANTAS_SALIDAS = 2

    fun registrarCapituloAbierto(context: Context) {
        try {
            if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val contador = prefs.getInt(KEY_CONTADOR, 0) + 1
            if (contador >= CADA_CUANTOS_CAPITULOS) {
                prefs.edit().putInt(KEY_CONTADOR, 0).apply()
                mostrarIntersticial(context)
            } else {
                prefs.edit().putInt(KEY_CONTADOR, contador).apply()
            }
        } catch (_: Throwable) { }
    }

    fun registrarSalidaDeLector(context: Context) {
        try {
            if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val contador = prefs.getInt(KEY_CONTADOR_SALIDA, 0) + 1
            if (contador >= CADA_CUANTAS_SALIDAS) {
                prefs.edit().putInt(KEY_CONTADOR_SALIDA, 0).apply()
                mostrarIntersticial(context)
            } else {
                prefs.edit().putInt(KEY_CONTADOR_SALIDA, contador).apply()
            }
        } catch (_: Throwable) { }
    }

    private fun mostrarIntersticial(context: Context) {
        try {
            StartAppAd.showAd(context)
        } catch (_: Exception) { }
    }

    fun ocultarBannersSiCorresponde(context: Context, vararg banners: View?) {
        try {
            if (!com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) return
            banners.forEach { it?.visibility = View.GONE }
        } catch (_: Throwable) { }
    }

    /** Los banners StartApp se cargan solos en el layout; no hace falta nada aquí. */
    fun cargarBanner(context: Context, container: View?) {
        // no-op: StartApp Banner en XML se auto-carga
    }
}
