package com.datgarscan.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.datgarscan.app.BuildConfig
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import java.lang.ref.WeakReference

/**
 * Unity Ads: rewarded, interstitial y banners.
 * Game ID: 6197188
 */
object UnityAdsManager {

    private const val TAG = "UnityAds"
    const val GAME_ID = "6197188"
    const val PLACEMENT_REWARDED = "Rewarded_Android"
    const val PLACEMENT_INTERSTITIAL = "Interstitial_Android"
    const val PLACEMENT_BANNER = "Banner_Android"

    @Volatile private var inicializado = false
    @Volatile private var iniciando = false
    @Volatile private var rewardedListo = false
    @Volatile private var interstitialListo = false

    // Banners pendientes hasta que Unity termine de inicializar
    private val bannersPendientes = mutableListOf<Pair<WeakReference<Activity>, WeakReference<ViewGroup>>>()

    fun inicializar(context: Context) {
        if (inicializado || iniciando) return
        iniciando = true
        try {
            val testMode = BuildConfig.DEBUG
            UnityAds.initialize(
                context.applicationContext, GAME_ID, testMode,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        inicializado = true
                        iniciando = false
                        Log.d(TAG, "Unity Ads listo (test=$testMode)")
                        precargarRewarded()
                        precargarInterstitial()
                        // Cargar banners que se pidieron antes de que Unity estuviera listo
                        val pendientes = synchronized(bannersPendientes) {
                            bannersPendientes.toList().also { bannersPendientes.clear() }
                        }
                        for ((actRef, contRef) in pendientes) {
                            val act = actRef.get()
                            val cont = contRef.get()
                            if (act != null && cont != null && !act.isFinishing) {
                                act.runOnUiThread { cargarBannerAhora(act, cont, reintento = true) }
                            }
                        }
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError?,
                        message: String?
                    ) {
                        inicializado = false
                        iniciando = false
                        Log.w(TAG, "Init falló: $error $message")
                    }
                }
            )
        } catch (e: Exception) {
            iniciando = false
            Log.e(TAG, "Init exception", e)
        }
    }

    fun estaInicializado(): Boolean = inicializado
    fun rewardedListo(): Boolean = inicializado && rewardedListo
    fun interstitialListo(): Boolean = inicializado && interstitialListo

    // ---- Rewarded ----

    fun precargarRewarded() {
        if (!inicializado) return
        try {
            rewardedListo = false
            UnityAds.load(PLACEMENT_REWARDED, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    rewardedListo = true
                }
                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?
                ) {
                    rewardedListo = false
                    Log.w(TAG, "Rewarded load: $error $message")
                }
            })
        } catch (e: Exception) {
            rewardedListo = false
        }
    }

    fun mostrarRewarded(activity: Activity, onCompletado: () -> Unit, onFallido: () -> Unit) {
        if (!rewardedListo()) {
            onFallido()
            precargarRewarded()
            return
        }
        try {
            UnityAds.show(activity, PLACEMENT_REWARDED, UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError?,
                        message: String?
                    ) {
                        rewardedListo = false
                        onFallido()
                        precargarRewarded()
                    }
                    override fun onUnityAdsShowStart(placementId: String) {}
                    override fun onUnityAdsShowClick(placementId: String) {}
                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState?
                    ) {
                        rewardedListo = false
                        if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                            onCompletado()
                        }
                        precargarRewarded()
                    }
                })
        } catch (e: Exception) {
            onFallido()
            precargarRewarded()
        }
    }

    // ---- Interstitial ----

    fun precargarInterstitial() {
        if (!inicializado) return
        try {
            interstitialListo = false
            UnityAds.load(PLACEMENT_INTERSTITIAL, object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    interstitialListo = true
                }
                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?
                ) {
                    interstitialListo = false
                    Log.w(TAG, "Interstitial load: $error $message")
                }
            })
        } catch (e: Exception) {
            interstitialListo = false
        }
    }

    fun mostrarInterstitial(context: Context) {
        val activity = context as? Activity ?: return
        if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) return
        if (!interstitialListo()) {
            precargarInterstitial()
            return
        }
        try {
            UnityAds.show(activity, PLACEMENT_INTERSTITIAL, UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError?,
                        message: String?
                    ) {
                        interstitialListo = false
                        precargarInterstitial()
                    }
                    override fun onUnityAdsShowStart(placementId: String) {}
                    override fun onUnityAdsShowClick(placementId: String) {}
                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState?
                    ) {
                        interstitialListo = false
                        precargarInterstitial()
                    }
                })
        } catch (e: Exception) {
            interstitialListo = false
            precargarInterstitial()
        }
    }

    // ---- Banner (carga tras init + reintento) ----

    fun cargarBanner(activity: Activity, container: ViewGroup?) {
        if (container == null) return
        if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(activity)) {
            container.visibility = android.view.View.GONE
            return
        }
        container.visibility = android.view.View.VISIBLE

        if (!inicializado) {
            // Encolar y arrancar Unity; se cargará en onInitializationComplete
            synchronized(bannersPendientes) {
                bannersPendientes.add(WeakReference(activity) to WeakReference(container))
            }
            inicializar(activity)
            Log.d(TAG, "Banner en cola (Unity aún iniciando)")
            return
        }
        cargarBannerAhora(activity, container, reintento = true)
    }

    private fun cargarBannerAhora(activity: Activity, container: ViewGroup, reintento: Boolean) {
        try {
            if (activity.isFinishing) return
            container.removeAllViews()
            val banner = BannerView(activity, PLACEMENT_BANNER, UnityBannerSize(320, 50))
            banner.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            banner.listener = object : BannerView.IListener {
                override fun onBannerLoaded(bannerAdView: BannerView?) {
                    Log.d(TAG, "Banner cargado")
                }
                override fun onBannerShown(bannerAdView: BannerView?) {}
                override fun onBannerClick(bannerAdView: BannerView?) {}
                override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
                    Log.w(TAG, "Banner fail: ${errorInfo?.errorMessage}")
                    // Un reintento a los 2s (red lenta / fill tardío)
                    if (reintento && !activity.isFinishing) {
                        container.postDelayed({
                            if (!activity.isFinishing) {
                                cargarBannerAhora(activity, container, reintento = false)
                            }
                        }, 2000)
                    }
                }
                override fun onBannerLeftApplication(bannerView: BannerView?) {}
            }
            container.addView(banner)
            banner.load()
        } catch (e: Exception) {
            Log.e(TAG, "Banner exception", e)
        }
    }
}
