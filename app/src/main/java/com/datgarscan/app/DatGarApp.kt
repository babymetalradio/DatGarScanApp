package com.datgarscan.app

import android.app.Application
import com.datgarscan.app.ads.UnityAdsManager

class DatGarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            UnityAdsManager.inicializar(this)
        } catch (_: Throwable) { }
    }
}
