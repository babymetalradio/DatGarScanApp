package com.datgarscan.app.activos

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.datgarscan.app.webapi.SesionManager
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Heartbeat de usuarios activos en tiempo real (1.9.9.8).
 *
 * Mientras la app está en primer plano y hay sesión, manda un ping
 * (POST api/activo.php) cada 60 segundos. El servidor guarda last_seen
 * del usuario y api/activos.php cuenta cuántos tienen last_seen de hace
 * menos de 5 minutos: esos son los activos en este momento.
 *
 * Ligero por diseño:
 *   - Sin servicios en segundo plano ni WorkManager: el ping solo vive
 *     mientras el proceso de la app existe y hay una Activity visible.
 *   - Sin dependencia nueva (no usa ProcessLifecycleOwner): el primer
 *     plano se detecta con ActivityLifecycleCallbacks de la Application.
 *   - Un fallo de red nunca rompe nada: se traga en silencio.
 *   - Sin sesión no se manda nada (el token vive en SesionManager y el
 *     interceptor de WebApiClient lo agrega como Bearer automáticamente).
 *
 * El token se carga en memoria en MainActivity.onCreate (SesionManager.cargar),
 * que corre después de Application.onCreate: las primeras vueltas del bucle
 * simplemente no mandan el ping hasta que la sesión está cargada.
 */
object ActivoManager {

    private const val TAG = "ActivoManager"
    private const val INTERVALO_MS = 60_000L

    @Volatile
    private var iniciado = false

    /** Activities visibles en este momento; > 0 = app en primer plano. */
    @Volatile
    private var actividadesVisibles = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Se llama una vez desde DatGarApp.onCreate(). Idempotente. */
    fun iniciar(app: Application) {
        if (iniciado) return
        iniciado = true

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val antes = actividadesVisibles
                actividadesVisibles++
                // Al volver a primer plano, ping inmediato (sin esperar al minuto).
                if (antes == 0) pingAhora()
            }

            override fun onActivityStopped(activity: Activity) {
                if (actividadesVisibles > 0) actividadesVisibles--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        scope.launch {
            while (true) {
                try {
                    if (actividadesVisibles > 0 && SesionManager.estaLogueado()) {
                        ping()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error en el bucle del heartbeat: ${e.message}")
                }
                delay(INTERVALO_MS)
            }
        }
    }

    /** Un ping inmediato, sin esperar al intervalo. Nunca lanza excepciones. */
    fun pingAhora() {
        scope.launch {
            try {
                if (SesionManager.estaLogueado()) ping()
            } catch (e: Exception) {
                Log.d(TAG, "pingAhora falló: ${e.message}")
            }
        }
    }

    /** La llamada de red en sí. El 401 lo maneja el interceptor (limpia sesión). */
    private suspend fun ping() {
        try {
            WebApiClient.get().pingActivo()
        } catch (e: Exception) {
            Log.d(TAG, "pingActivo falló: ${e.message}")
        }
    }
}
