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
 * Rama test-inmobi: SOLO InMobi (banner, interstitial, rewarded).
 * Account: e8325a72173441c488213372e95da99c
 */
object InMobiAdsManager {

    private const val TAG = "InMobi"
    const val ACCOUNT_ID = "e8325a72173441c488213372e95da99c"
    const val PLACEMENT_BANNER = 10000829296L
    const val PLACEMENT_INTERSTITIAL = 10000829295L
    const val PLACEMENT_REWARDED = 10000835637L

    @Volatile private var inicializado = false

    private var interstitial: InMobiInterstitial? = null
    @Volatile private var interstitialListo = false

    private var rewarded: InMobiInterstitial? = null
    @Volatile private var rewardedListo = false
    private var onRewardedCompletado: (() -> Unit)? = null
    private var onRewardedFallido: (() -> Unit)? = null
    private var recompensaOtorgada = false

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
                            Log.d(TAG, "InMobi init OK (solo InMobi)")
                        } else {
                            Log.e(TAG, "InMobi init falló: ${error.message}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "init exception", e)
        }
    }

    fun estaListo(): Boolean = inicializado

    // ---- Interstitial ----

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
                override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                    interstitialListo = true
                    Log.d(TAG, "Interstitial listo")
                }
                override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                    interstitialListo = false
                    Log.w(TAG, "Interstitial fail: ${status.message}")
                }
                override fun onAdDismissed(ad: InMobiInterstitial) {
                    interstitialListo = false
                    cargarInterstitial(activity)
                }
                override fun onAdDisplayFailed(ad: InMobiInterstitial) {
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
                precargarInterstitial(activity)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "mostrar interstitial", e)
            false
        }
    }

    // ---- Rewarded (placement rewarded InMobi) ----

    fun rewardedListo(): Boolean = inicializado && rewardedListo

    fun precargarRewarded(activity: Activity) {
        if (!inicializado) {
            inicializar(activity)
            activity.window?.decorView?.postDelayed({
                if (inicializado) cargarRewarded(activity)
            }, 2000)
            return
        }
        cargarRewarded(activity)
    }

    private fun cargarRewarded(activity: Activity) {
        try {
            rewardedListo = false
            val listener = object : InterstitialAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                    rewardedListo = true
                    Log.d(TAG, "Rewarded listo")
                }
                override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                    rewardedListo = false
                    Log.w(TAG, "Rewarded fail: ${status.message}")
                }
                override fun onAdDismissed(ad: InMobiInterstitial) {
                    rewardedListo = false
                    val ok = recompensaOtorgada
                    recompensaOtorgada = false
                    if (ok) onRewardedCompletado?.invoke() else onRewardedFallido?.invoke()
                    onRewardedCompletado = null
                    onRewardedFallido = null
                    cargarRewarded(activity)
                }
                override fun onAdDisplayFailed(ad: InMobiInterstitial) {
                    rewardedListo = false
                    onRewardedFallido?.invoke()
                    onRewardedCompletado = null
                    onRewardedFallido = null
                    cargarRewarded(activity)
                }
                override fun onRewardsUnlocked(ad: InMobiInterstitial, rewards: Map<Any, Any>?) {
                    recompensaOtorgada = true
                    Log.d(TAG, "Rewarded unlocked: $rewards")
                }
            }
            val ad = InMobiInterstitial(activity, PLACEMENT_REWARDED, listener)
            rewarded = ad
            ad.load()
        } catch (e: Exception) {
            Log.e(TAG, "cargar rewarded", e)
            rewardedListo = false
        }
    }

    fun mostrarRewarded(
        activity: Activity,
        onCompletado: () -> Unit,
        onFallido: () -> Unit
    ) {
        if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(activity)) {
            // sin anuncios: dar recompensa sin video
            onCompletado()
            return
        }
        if (!rewardedListo()) {
            onFallido()
            precargarRewarded(activity)
            return
        }
        try {
            recompensaOtorgada = false
            onRewardedCompletado = onCompletado
            onRewardedFallido = onFallido
            rewardedListo = false
            rewarded?.show()
        } catch (e: Exception) {
            Log.e(TAG, "mostrar rewarded", e)
            onFallido()
            precargarRewarded(activity)
        }
    }

    // ---- Banner ----

    fun cargarBanner(activity: Activity, container: ViewGroup?) {
        if (container == null) return
        try {
            if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(activity)) {
                container.visibility = android.view.View.GONE
                return
            }
            if (!inicializado) inicializar(activity)
            container.visibility = android.view.View.VISIBLE
            container.removeAllViews()
            val banner = InMobiBanner(activity, PLACEMENT_BANNER)
            banner.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            try { banner.setBannerSize(320, 50) } catch (_: Exception) { }
            banner.setListener(object : BannerAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiBanner, info: AdMetaInfo) {
                    Log.d(TAG, "Banner cargado")
                }
                override fun onAdLoadFailed(ad: InMobiBanner, status: InMobiAdRequestStatus) {
                    Log.w(TAG, "Banner fail: ${status.message}")
                }
            })
            container.addView(banner)
            banner.load()
        } catch (e: Exception) {
            Log.e(TAG, "banner", e)
        }
    }
}
