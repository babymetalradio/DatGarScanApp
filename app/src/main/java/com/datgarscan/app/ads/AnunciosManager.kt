package com.datgarscan.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.datgarscan.app.BuildConfig
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdEventListener

/**
 * Intersticiales: InMobi → fallback StartApp
 * Banners: Unity Ads
 * Rewarded: Unity (Tienda)
 */
object AnunciosManager {

    private const val TAG = "Ads"
    private const val PREFS = "datgar_ads"
    private const val KEY_CONTADOR = "capitulos_abiertos"
    private const val KEY_CONTADOR_SALIDA = "salidas_lector"
    private const val CADA_CUANTOS_CAPITULOS = 1
    private const val CADA_CUANTAS_SALIDAS = 2
    private const val STARTAPP_ID = "207366634"

    @Volatile private var startAppInit = false
    private var startAppInterstitial: StartAppAd? = null
    @Volatile private var startAppListo = false

    fun inicializar(context: Context) {
        try {
            InMobiAdsManager.inicializar(context)
            if (context is Activity) {
                InMobiAdsManager.precargarInterstitial(context)
            }
            if (!startAppInit) {
                StartAppSDK.init(context.applicationContext, STARTAPP_ID, false)
                StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
                StartAppSDK.enableReturnAds(false)
                startAppInit = true
                precargarStartApp(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "init", e)
        }
    }

    private fun precargarStartApp(context: Context) {
        try {
            val ad = StartAppAd(context.applicationContext)
            startAppListo = false
            ad.loadAd(object : AdEventListener {
                override fun onReceiveAd(ad: Ad) { startAppListo = true }
                override fun onFailedToReceiveAd(ad: Ad?) { startAppListo = false }
            })
            startAppInterstitial = ad
        } catch (_: Exception) { }
    }

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
            val activity = context as? Activity
            if (activity != null) {
                if (InMobiAdsManager.mostrarInterstitial(activity)) return
            }
            // Fallback StartApp
            val ad = startAppInterstitial
            if (ad != null && startAppListo) {
                startAppListo = false
                ad.showAd()
                precargarStartApp(context)
            } else {
                try { StartAppAd.showAd(context) } catch (_: Exception) { }
                precargarStartApp(context)
                if (activity != null) InMobiAdsManager.precargarInterstitial(activity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "interstitial", e)
        }
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
            // Banner: Unity (más rápido cuando ya está inicializado)
            UnityAdsManager.cargarBanner(activity, vg)
        } catch (e: Exception) {
            Log.e(TAG, "banner", e)
        }
    }
}
