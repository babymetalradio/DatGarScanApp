package com.datgarscan.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup

/**
 * Rama test-inmobi: SOLO InMobi (banner + interstitial).
 * Rewarded sigue en Unity (Tienda) hasta crear placement rewarded InMobi.
 */
object AnunciosManager {

    private const val TAG = "AdsInMobi"
    private const val PREFS = "datgar_ads"
    private const val KEY_CONTADOR = "capitulos_abiertos"
    private const val KEY_CONTADOR_SALIDA = "salidas_lector"
    private const val CADA_CUANTOS_CAPITULOS = 1
    private const val CADA_CUANTAS_SALIDAS = 2

    fun inicializar(context: Context) {
        try {
            InMobiAdsManager.inicializar(context)
            if (context is Activity) {
                InMobiAdsManager.precargarInterstitial(context)
            }
            // Unity solo por si la tienda pide rewarded
            try { UnityAdsManager.inicializar(context) } catch (_: Exception) { }
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
                val activity = context as? Activity
                if (activity != null) {
                    InMobiAdsManager.mostrarInterstitial(activity)
                }
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
                val activity = context as? Activity
                if (activity != null) {
                    InMobiAdsManager.mostrarInterstitial(activity)
                }
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

    fun cargarBanner(context: Context, container: View?) {
        try {
            val activity = context as? Activity ?: return
            val vg = container as? ViewGroup ?: return
            InMobiAdsManager.cargarBanner(activity, vg)
        } catch (e: Exception) {
            Log.e(TAG, "banner", e)
        }
    }
}
