package com.datgarscan.app

import android.app.Application
import com.datgarscan.app.activos.ActivoManager
import com.datgarscan.app.ads.InMobiAdsManager
import com.datgarscan.app.ads.UnityAdsManager

class DatGarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            UnityAdsManager.inicializar(this)
            InMobiAdsManager.inicializar(this)
        } catch (_: Throwable) { }
        try {
            // Heartbeat de usuarios activos: ping cada 60 s solo en primer plano.
            ActivoManager.iniciar(this)
        } catch (_: Throwable) { }
    }
}
