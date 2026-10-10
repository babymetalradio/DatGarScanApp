package com.datgarscan.app

import android.app.Application
import com.datgarscan.app.ads.InMobiAdsManager

class DatGarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            InMobiAdsManager.inicializar(this)
        } catch (_: Throwable) { }
    }
}
