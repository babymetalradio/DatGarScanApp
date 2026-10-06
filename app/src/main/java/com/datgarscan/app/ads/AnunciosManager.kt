package com.datgarscan.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup

/**
 * Rama test-unity: SOLO Unity Ads (banner, interstitial).
 * Rewarded también Unity (Tienda).
 */
object AnunciosManager {

    private const val TAG = "AdsUnity"
    private const val PREFS = "datgar_ads"
    private const val KEY_CONTADOR = "capitulos_abiertos"
    private const val KEY_CONTADOR_SALIDA = "salidas_lector"
    private const val CADA_CUANTOS_CAPITULOS = 4
    private const val CADA_CUANTAS_SALIDAS = 6

    fun inicializar(context: Context) {
        try {
            UnityAdsManager.inicializar(context)
        } catch (e: Exception) {
            Log.e(TAG, "init", e)
        }
    }

    fun registrarCapituloAbierto(context: Context) {
        try {
            if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val contador = prefs.getInt(KEY_CONTADOR, 0) + 1
            if (contador >= CADA_CUANTOS_CAPITULOS) {
                prefs.edit().putInt(KEY_CONTADOR, 0).apply()
                UnityAdsManager.mostrarInterstitial(context)
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
                UnityAdsManager.mostrarInterstitial(context)
            } else {
                prefs.edit().putInt(KEY_CONTADOR_SALIDA, contador).apply()
            }
        } catch (_: Throwable) { }
    }

    fun ocultarBannersSiCorresponde(context: Context, vararg banners: View?) {
        try {
            if (!com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) return
            banners.forEach { it?.visibility = View.GONE }
        } catch (_: Throwable) { }
    }

    fun cargarBanner(context: Context, container: View?, estable: Boolean = false) {
        try {
            val activity = context as? Activity ?: return
            val vg = container as? ViewGroup ?: return
            UnityAdsManager.cargarBanner(activity, vg, estable)
        } catch (e: Exception) {
            Log.e(TAG, "banner", e)
        }
    }
}
