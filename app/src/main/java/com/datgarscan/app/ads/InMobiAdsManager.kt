package com.datgarscan.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.inmobi.ads.AdMetaInfo
import com.inmobi.ads.InMobiAdRequestStatus
import com.inmobi.ads.InMobiBanner
import com.inmobi.ads.InMobiInterstitial
import com.inmobi.ads.listeners.BannerAdEventListener
import com.inmobi.ads.listeners.InterstitialAdEventListener
import com.inmobi.sdk.InMobiSdk
import com.inmobi.sdk.SdkInitializationListener
import org.json.JSONObject

/**
 * InMobi (prueba en main unos días).
 * Account: e8325a72173441c488213372e95da99c
 */
object InMobiAdsManager {

    private const val TAG = "InMobi"
    const val ACCOUNT_ID = "e8325a72173441c488213372e95da99c"
    const val PLACEMENT_BANNER = 10000829296L
    const val PLACEMENT_INTERSTITIAL = 10000829295L

    @Volatile private var inicializado = false
    private var interstitial: InMobiInterstitial? = null
    @Volatile private var interstitialListo = false

    fun inicializar(context: Context) {
        if (inicializado) return
        try {
            val consent = JSONObject()
            try {
                consent.put(InMobiSdk.IM_GDPR_CONSENT_AVAILABLE, true)
            } catch (_: Exception) { }

            InMobiSdk.init(
                context.applicationContext,
                ACCOUNT_ID,
                consent,
                object : SdkInitializationListener {
                    override fun onInitializationComplete(error: Error?) {
                        if (error == null) {
                            inicializado = true
                            Log.d(TAG, "InMobi init OK")
                        } else {
                            Log.e(TAG, "InMobi init falló: ${error.message}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "InMobi init exception", e)
        }
    }

    fun estaListo(): Boolean = inicializado

    fun precargarInterstitial(activity: Activity) {
        if (!inicializado) {
            inicializar(activity)
            activity.window?.decorView?.postDelayed({
                if (inicializado) cargarInterstitial(activity)
            }, 2000)
            return
        }
        cargarInterstitial(activity)
    }

    private fun cargarInterstitial(activity: Activity) {
        try {
            interstitialListo = false
            val listener = object : InterstitialAdEventListener() {
                override fun onAdLoadSucceeded(
                    inMobiInterstitial: InMobiInterstitial,
                    adMetaInfo: AdMetaInfo
                ) {
                    interstitialListo = true
                    Log.d(TAG, "Interstitial listo")
                }

                override fun onAdLoadFailed(
                    inMobiInterstitial: InMobiInterstitial,
                    status: InMobiAdRequestStatus
                ) {
                    interstitialListo = false
                    Log.w(TAG, "Interstitial fail: ${status.message}")
                }

                override fun onAdDismissed(inMobiInterstitial: InMobiInterstitial) {
                    interstitialListo = false
                    cargarInterstitial(activity)
                }

                override fun onAdDisplayFailed(inMobiInterstitial: InMobiInterstitial) {
                    interstitialListo = false
                    cargarInterstitial(activity)
                }
            }
            val ad = InMobiInterstitial(activity, PLACEMENT_INTERSTITIAL, listener)
            interstitial = ad
            ad.load()
        } catch (e: Exception) {
            Log.e(TAG, "cargar interstitial", e)
        }
    }

    /** true si se mostró */
    fun mostrarInterstitial(activity: Activity): Boolean {
        return try {
            if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(activity)) return false
            if (!inicializado) {
                inicializar(activity)
                return false
            }
            val ad = interstitial
            if (ad != null && interstitialListo) {
                interstitialListo = false
                ad.show()
                true
            } else {
                Log.w(TAG, "Interstitial no listo")
                precargarInterstitial(activity)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "mostrar interstitial", e)
            false
        }
    }

    fun cargarBanner(activity: Activity, container: ViewGroup?) {
        if (container == null) return
        try {
            if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(activity)) {
                container.visibility = android.view.View.GONE
                return
            }
            if (!inicializado) {
                inicializar(activity)
            }
            container.visibility = android.view.View.VISIBLE
            container.removeAllViews()
            val banner = InMobiBanner(activity, PLACEMENT_BANNER)
            banner.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            try {
                banner.setBannerSize(320, 50)
            } catch (_: Exception) { }
            banner.setListener(object : BannerAdEventListener() {
                override fun onAdLoadSucceeded(
                    inMobiBanner: InMobiBanner,
                    adMetaInfo: AdMetaInfo
                ) {
                    Log.d(TAG, "Banner InMobi cargado")
                }

                override fun onAdLoadFailed(
                    inMobiBanner: InMobiBanner,
                    status: InMobiAdRequestStatus
                ) {
                    Log.w(TAG, "Banner InMobi fail: ${status.message} → Unity")
                    // Fallback Unity si InMobi no llena
                    try {
                        UnityAdsManager.cargarBanner(activity, container, estable = false)
                    } catch (e: Exception) {
                        Log.e(TAG, "fallback Unity banner", e)
                    }
                }
            })
            container.addView(banner)
            banner.load()
        } catch (e: Exception) {
            Log.e(TAG, "banner", e)
            try {
                UnityAdsManager.cargarBanner(activity, container, estable = false)
            } catch (_: Exception) { }
        }
    }
}
