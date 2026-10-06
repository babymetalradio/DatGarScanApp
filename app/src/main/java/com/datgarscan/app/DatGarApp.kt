package com.datgarscan.app

import android.app.Application
import com.datgarscan.app.ads.UnityAdsManager

/**
 * Arranca Unity Ads lo antes posible (antes de MainActivity)
 * para que el banner tarde menos en aparecer.
 */
class DatGarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            UnityAdsManager.inicializar(this)
        } catch (_: Throwable) { }
    }
}
