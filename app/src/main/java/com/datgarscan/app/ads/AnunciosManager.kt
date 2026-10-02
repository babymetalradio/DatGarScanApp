package com.datgarscan.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import com.datgarscan.app.BuildConfig
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdEventListener

/**
 * Banners e intersticiales: StartApp.
 * Rewarded (garritas): Unity Ads.
 */
object AnunciosManager {

    private const val TAG = "StartApp"
    private const val PREFS = "datgar_ads"
    private const val KEY_CONTADOR = "capitulos_abiertos"
    private const val KEY_CONTADOR_SALIDA = "salidas_lector"
    private const val CADA_CUANTOS_CAPITULOS = 1
    private const val CADA_CUANTAS_SALIDAS = 2
    private const val APP_ID = "207366634"

    @Volatile private var inicializado = false
    private var interstitial: StartAppAd? = null
    @Volatile private var interstitialListo = false

    /** Llamar una vez al arrancar (MainActivity.onCreate). */
    fun inicializar(context: Context) {
        if (inicializado) return
        try {
            // init explícito (más fiable que solo meta-data)
            StartAppSDK.init(context.applicationContext, APP_ID, false)
            // En debug muestra anuncios de prueba de StartApp
            StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
            StartAppSDK.enableReturnAds(false)
            inicializado = true
            Log.d(TAG, "StartApp init OK (test=${BuildConfig.DEBUG})")
            precargarIntersticial(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "StartApp init falló", e)
        }
    }

    fun registrarCapituloAbierto(context: Context) {
        try {
            if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) {
                Log.d(TAG, "Sin anuncios activo → no intersticial")
                return
            }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val contador = prefs.getInt(KEY_CONTADOR, 0) + 1
            if (contador >= CADA_CUANTOS_CAPITULOS) {
                prefs.edit().putInt(KEY_CONTADOR, 0).apply()
                mostrarIntersticial(context)
            } else {
                prefs.edit().putInt(KEY_CONTADOR, contador).apply()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "registrarCapitulo", e)
        }
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
        } catch (e: Throwable) {
            Log.e(TAG, "registrarSalida", e)
        }
    }

    private fun precargarIntersticial(context: Context) {
        try {
            val ctx = context.applicationContext
            val ad = StartAppAd(ctx)
            interstitialListo = false
            ad.loadAd(object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    interstitialListo = true
                    Log.d(TAG, "Intersticial listo")
                }
                override fun onFailedToReceiveAd(ad: Ad?) {
                    interstitialListo = false
                    Log.w(TAG, "Intersticial no disponible")
                }
            })
            interstitial = ad
        } catch (e: Exception) {
            Log.e(TAG, "precargar intersticial", e)
        }
    }

    private fun mostrarIntersticial(context: Context) {
        try {
            if (!inicializado) inicializar(context)
            val ad = interstitial
            if (ad != null && interstitialListo) {
                interstitialListo = false
                ad.showAd()
                // Precargar el siguiente
                precargarIntersticial(context)
            } else {
                Log.w(TAG, "Intersticial no listo, reintentando carga")
                // Fallback: showAd directo (comportamiento antiguo)
                try {
                    StartAppAd.showAd(context)
                } catch (_: Exception) { }
                precargarIntersticial(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "mostrar intersticial", e)
            precargarIntersticial(context)
        }
    }

    fun ocultarBannersSiCorresponde(context: Context, vararg banners: View?) {
        try {
            if (!com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) return
            banners.forEach { it?.visibility = View.GONE }
        } catch (_: Throwable) { }
    }

    fun cargarBanner(context: Context, container: View?) {
        // Banner en XML se auto-carga; solo asegurar visibilidad si no hay Pro
        try {
            if (container == null) return
            if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) {
                container.visibility = View.GONE
            } else {
                container.visibility = View.VISIBLE
            }
        } catch (_: Throwable) { }
    }
}
