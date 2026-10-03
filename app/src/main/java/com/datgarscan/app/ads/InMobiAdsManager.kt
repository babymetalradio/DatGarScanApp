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
 * InMobi: banners e intersticiales.
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
            val consent = JSONObject().apply {
                put(InMobiSdk.IM_GDPR_CONSENT_AVAILABLE, true)
            }
            InMobiSdk.init(context.applicationContext, ACCOUNT_ID, consent,
                SdkInitializationListener { error ->
                    if (error == null) {
                        inicializado = true
                        Log.d(TAG, "InMobi init OK")
                        precargarInterstitial(context.applicationContext)
                    } else {
                        Log.e(TAG, "InMobi init falló: ${error.message}")
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "InMobi init exception", e)
        }
    }

    fun estaListo(): Boolean = inicializado

    fun precargarInterstitial(context: Context) {
        if (!inicializado) return
        try {
            val appCtx = context.applicationContext
            // InMobiInterstitial necesita Activity a veces; usamos app context y Activity al show
            interstitialListo = false
        } catch (e: Exception) {
            Log.e(TAG, "precarga", e)
        }
    }

    fun precargarInterstitial(activity: Activity) {
        if (!inicializado) {
            inicializar(activity)
            return
        }
        try {
            interstitialListo = false
            val ad = InMobiInterstitial(activity, PLACEMENT_INTERSTITIAL, object : InterstitialAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                    interstitialListo = true
                    Log.d(TAG, "Interstitial listo")
                }
                override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                    interstitialListo = false
                    Log.w(TAG, "Interstitial fail: ${status.statusCode} ${status.message}")
                }
                override fun onAdDismissed(ad: InMobiInterstitial) {
                    interstitialListo = false
                    precargarInterstitial(activity)
                }
                override fun onAdDisplayFailed(ad: InMobiInterstitial) {
                    interstitialListo = false
                    precargarInterstitial(activity)
                }
            })
            interstitial = ad
            ad.load()
        } catch (e: Exception) {
            Log.e(TAG, "precargar interstitial", e)
        }
    }

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
                // reintentar cuando init complete es complejo; intentar igual
            }
            container.visibility = android.view.View.VISIBLE
            container.removeAllViews()
            val banner = InMobiBanner(activity, PLACEMENT_BANNER)
            banner.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            banner.setBannerSize(320, 50)
            banner.setListener(object : BannerAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiBanner, info: AdMetaInfo) {
                    Log.d(TAG, "Banner cargado")
                }
                override fun onAdLoadFailed(ad: InMobiBanner, status: InMobiAdRequestStatus) {
                    Log.w(TAG, "Banner fail: ${status.statusCode} ${status.message}")
                }
            })
            container.addView(banner)
            banner.load()
        } catch (e: Exception) {
            Log.e(TAG, "banner", e)
        }
    }
}
