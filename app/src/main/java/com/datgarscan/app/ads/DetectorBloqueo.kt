package com.datgarscan.app.ads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Opción 2: detecta bloqueadores de anuncios (DNS privado / VPN / resolución).
 * No modifica el DNS del sistema; solo informa.
 */
object DetectorBloqueo {

    private const val TAG = "DetectorBloqueo"

    /** Dominio que debería resolver siempre si hay internet real. */
    private const val DOMINIO_CONTROL = "dns.google"

    /** Dominios típicos de redes de anuncios usadas por la app. */
    private val DOMINIOS_ADS = listOf(
        "unityads.unity3d.com",
        "auction.unityads.unity3d.com",
        "config.unityads.unity3d.com"
    )

    private val HOSTS_DNS_BLOQUEADORES = listOf(
        "adguard",
        "adguard-dns",
        "dns.adguard",
        "nextdns",
        "controlD",
        "controld",
        "dns.controld",
        "blokada",
        "dnsforge",
        "applied-privacy",
        "cleanbrowsing",
        "antia.com" // por si acaso
    )

    data class Resultado(
        val bloqueado: Boolean,
        val motivo: String = "",
        val hostnameDns: String? = null,
        val vpn: Boolean = false,
        val adsResuelven: Boolean = true
    )

    /**
     * Ejecutar en hilo de fondo (hace DNS lookups).
     * Pro / sin anuncios → nunca bloqueado.
     */
    fun evaluar(context: Context): Resultado {
        try {
            if (com.datgarscan.app.tienda.SinAnunciosManager.tieneSinAnuncios(context)) {
                return Resultado(false, "sin_anuncios")
            }
            if (com.datgarscan.app.tienda.ProManager.esPro(context)) {
                return Resultado(false, "pro")
            }
        } catch (_: Throwable) { }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return Resultado(false, "sin_cm")

        val red = cm.activeNetwork
        val caps = red?.let { cm.getNetworkCapabilities(it) }
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            // Sin red: no bloqueamos (offline / descargas)
            return Resultado(false, "sin_red")
        }

        val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        var hostname: String? = null
        var privateDnsActivo = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && red != null) {
            try {
                val lp = cm.getLinkProperties(red)
                privateDnsActivo = lp?.isPrivateDnsActive == true
                hostname = lp?.privateDnsServerName
            } catch (e: Exception) {
                Log.w(TAG, "privateDns", e)
            }
        }

        val hostSospechoso = hostname?.lowercase()?.let { h ->
            HOSTS_DNS_BLOQUEADORES.any { h.contains(it) }
        } == true

        // Control de falsos positivos: si ni el dominio de control resuelve, es red mala
        val controlOk = resuelve(DOMINIO_CONTROL)
        if (!controlOk) {
            return Resultado(false, "red_mala", hostname, vpn, adsResuelven = true)
        }

        // Prueba de ads (2 intentos)
        var adsOk = false
        repeat(2) {
            if (DOMINIOS_ADS.any { resuelve(it) }) {
                adsOk = true
                return@repeat
            }
        }

        val bloqueadoPorResolucion = !adsOk
        val bloqueadoPorDns = privateDnsActivo && hostSospechoso
        // VPN sola no basta (muchos usan VPN legítima); suma si además fallan ads
        val bloqueadoPorVpn = vpn && bloqueadoPorResolucion

        val bloqueado = bloqueadoPorResolucion || bloqueadoPorDns || bloqueadoPorVpn
        val motivo = when {
            bloqueadoPorResolucion && bloqueadoPorDns -> "dns+resolucion"
            bloqueadoPorResolucion && bloqueadoPorVpn -> "vpn+resolucion"
            bloqueadoPorResolucion -> "resolucion"
            bloqueadoPorDns -> "dns_privado"
            else -> "ok"
        }

        Log.d(TAG, "bloqueado=$bloqueado motivo=$motivo host=$hostname vpn=$vpn adsOk=$adsOk")
        return Resultado(bloqueado, motivo, hostname, vpn, adsResuelven = adsOk)
    }

    private fun resuelve(host: String): Boolean {
        return try {
            val addrs = InetAddress.getAllByName(host)
            if (addrs.isNullOrEmpty()) return false
            // 0.0.0.0 / :: usados por algunos bloqueadores
            addrs.any { addr ->
                val b = addr.address
                !(b.all { it == 0.toByte() })
            }
        } catch (_: UnknownHostException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
