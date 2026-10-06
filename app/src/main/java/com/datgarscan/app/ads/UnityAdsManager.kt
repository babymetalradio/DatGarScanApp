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

    // true = el APK debug usa anuncios REALES (para probar el fill de verdad).
    // No afecta al release (siempre es real). Con true NO des clic a los anuncios.
    // Pon false para volver a anuncios de prueba en debug.
    private const val REALES_EN_DEBUG = true

    private var reintentosInit = 0

    @Volatile private var inicializado = false
    @Volatile private var iniciando = false
    @Volatile private var rewardedListo = false
    @Volatile private var interstitialListo = false

    private val bannersPendientes =
        mutableListOf<Pair<WeakReference<Activity>, WeakReference<ViewGroup>>>()

    fun inicializar(context: Context) {
        if (inicializado || iniciando) return
        iniciando = true
        try {
            val testMode = BuildConfig.DEBUG && !REALES_EN_DEBUG
            UnityAds.initialize(
                context.applicationContext,
                GAME_ID,
                testMode,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        inicializado = true
                        iniciando = false
                        Log.d(TAG, "Unity Ads listo (test=$testMode)")
                        reintentosInit = 0
                        precargarRewarded()
                        precargarInterstitial()
                        val pendientes = synchronized(bannersPendientes) {
                            bannersPendientes.toList().also { bannersPendientes.clear() }
                        }
                        for ((actRef, contRef) in pendientes) {
                            val act = actRef.get()
                            val cont = contRef.get()
                            if (act != null && cont != null && !act.isFinishing) {
                                act.runOnUiThread {
                                    cargarBannerAhora(act, cont, intento = 0)
                                }
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
                        // Reintenta (los banners en cola siguen esperando)
                        if (reintentosInit < 3) {
                            reintentosInit++
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                                { inicializar(context) }, 5000L * reintentosInit
                            )
                        }
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
            UnityAds.show(
                activity,
                PLACEMENT_REWARDED,
                UnityAdsShowOptions(),
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
                }
            )
        } catch (e: Exception) {
            onFallido()
            precargarRewarded()
        }
    }

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
            UnityAds.show(
                activity,
                PLACEMENT_INTERSTITIAL,
                UnityAdsShowOptions(),
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
                }
            )
        } catch (e: Exception) {
            interstitialListo = false
            precargarInterstitial()
        }
    }

    /**
     * [estable] = true (lector): el espacio del banner siempre queda reservado y
     * no se hacen reintentos largos, para que la lectura nunca se mueva.
     */
    fun cargarBanner(activity: Activity, container: ViewGroup?, estable: Boolean = false) {
        if (container == null) return
        container.tag = if (estable) TAG_ESTABLE else null
        if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(activity)) {
            container.visibility = android.view.View.GONE
            return
        }
        container.visibility = android.view.View.VISIBLE

        if (!inicializado) {
            synchronized(bannersPendientes) {
                bannersPendientes.add(WeakReference(activity) to WeakReference(container))
            }
            inicializar(activity)
            Log.d(TAG, "Banner en cola (Unity aún iniciando)")
            return
        }
        cargarBannerAhora(activity, container, intento = 0)
    }

    // Unity a veces no tiene anuncio de banner ("no fill"). Reintentamos con
    // esperas crecientes y, mientras no hay anuncio, el espacio se colapsa.
    private const val MAX_INTENTOS_BANNER = 4
    private const val TAG_ESTABLE = "banner_estable"
    private val ESPERA_BANNER_MS = longArrayOf(0L, 20_000L, 45_000L, 90_000L)

    private fun cargarBannerAhora(activity: Activity, container: ViewGroup, intento: Int) {
        try {
            if (activity.isFinishing) return
            val estable = container.tag == TAG_ESTABLE
            container.removeAllViews()
            container.visibility = android.view.View.VISIBLE
            val banner = BannerView(activity, PLACEMENT_BANNER, UnityBannerSize(320, 50))
            banner.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            banner.listener = object : BannerView.IListener {
                override fun onBannerLoaded(bannerAdView: BannerView?) {
                    Log.d(TAG, "Banner cargado")
                    container.visibility = android.view.View.VISIBLE
                }

                override fun onBannerShown(bannerAdView: BannerView?) {}
                override fun onBannerClick(bannerAdView: BannerView?) {}

                override fun onBannerFailedToLoad(
                    bannerAdView: BannerView?,
                    errorInfo: BannerErrorInfo?
                ) {
                    Log.w(TAG, "Banner fail (intento $intento): ${errorInfo?.errorMessage}")
                    try { banner.destroy() } catch (_: Throwable) { }
                    container.removeAllViews()

                    if (estable) {
                        // Lector: el espacio queda reservado (la lectura no se mueve).
                        // Solo un reintento rapido, como antes.
                        if (intento == 0 && !activity.isFinishing) {
                            container.postDelayed({
                                if (!activity.isFinishing) cargarBannerAhora(activity, container, 1)
                            }, 800)
                        }
                        return
                    }

                    container.visibility = android.view.View.GONE
                    val siguiente = intento + 1
                    if (siguiente < MAX_INTENTOS_BANNER && !activity.isFinishing) {
                        container.postDelayed({
                            if (!activity.isFinishing &&
                                !com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(activity)
                            ) {
                                cargarBannerAhora(activity, container, siguiente)
                            }
                        }, ESPERA_BANNER_MS[siguiente])
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
