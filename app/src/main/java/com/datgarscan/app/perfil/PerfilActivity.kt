package com.datgarscan.app.perfil

import com.datgarscan.app.BaseActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.datgarscan.app.R
import com.datgarscan.app.ads.UnityAdsManager
import com.datgarscan.app.databinding.ActivityPerfilBinding
import com.datgarscan.app.rachas.RachaManager
import com.datgarscan.app.webapi.ErroresRed
import com.datgarscan.app.webapi.PerfilData
import com.datgarscan.app.webapi.RachaEstado
import com.datgarscan.app.webapi.SesionManager
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class PerfilActivity : BaseActivity() {

    companion object {
        fun crearIntent(context: Context): Intent = Intent(context, PerfilActivity::class.java)
    }

    private lateinit var binding: ActivityPerfilBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try { com.datgarscan.app.ads.AnunciosManager.cargarBanner(this, binding.bannerPerfil) } catch (_: Throwable) {}

        if (!SesionManager.estaLogueado()) {
            Toast.makeText(this, "Inicia sesión para ver tu perfil.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvVolver.setOnClickListener { finish() }
        binding.btnTienda.setOnClickListener {
            startActivity(com.datgarscan.app.tienda.TiendaActivity.crearIntent(this))
        }
        binding.btnFavoritos.setOnClickListener {
            startActivity(com.datgarscan.app.favoritos.FavoritosActivity.crearIntent(this))
        }
        binding.btnHistorial.setOnClickListener {
            startActivity(com.datgarscan.app.historial.HistorialActivity.crearIntent(this))
        }
        binding.btnDescargas.setOnClickListener {
            startActivity(com.datgarscan.app.descargas.DescargasActivity.crearIntent(this))
        }

        binding.btnCerrarSesion.setOnClickListener { confirmarCerrarSesion() }

        // Precarga el anuncio recompensado para que esté listo cuando el
        // usuario quiera cobrar la recompensa de un hito de racha.
        UnityAdsManager.precargarRewarded()

        // Bottom nav
        binding.navInicio.setOnClickListener {
            startActivity(android.content.Intent(this, com.datgarscan.app.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
        binding.navExplorar.setOnClickListener {
            startActivity(android.content.Intent(this, com.datgarscan.app.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
        binding.navFavoritos.setOnClickListener {
            startActivity(com.datgarscan.app.favoritos.FavoritosActivity.crearIntent(this))
        }
        binding.navSeries.setOnClickListener {
            startActivity(com.datgarscan.app.historial.HistorialActivity.crearIntent(this))
        }
        binding.navPerfil.setOnClickListener {
            // Ya estamos aquí
        }

        // Muestra de inmediato lo que ya sabemos, sin esperar al servidor
        binding.tvUsername.text = SesionManager.usernameEnMemoria ?: ""
        binding.tvInicial.text = (SesionManager.usernameEnMemoria ?: "?").take(1).uppercase()
    }

    override fun onResume() {
        super.onResume()
        cargarPerfil()
        cargarRacha()
        com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerPerfil)
    }

    private fun cargarPerfil() {
        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().obtenerPerfil()
                if (respuesta.success && respuesta.data != null) {
                    pintarPerfil(respuesta.data)
                }
            } catch (e: Exception) {
                Toast.makeText(this@PerfilActivity, ErroresRed.mensajeAmable(e), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pintarPerfil(perfil: PerfilData) {
        binding.tvUsername.text = perfil.username
        binding.tvInicial.text = perfil.username.take(1).uppercase()

        binding.tvNumFavoritos.text = perfil.favoritos.toString()
        binding.tvNumCapitulos.text = perfil.capitulos_leidos.toString()
        binding.tvNumSeries.text = perfil.series_leidas.toString()

        binding.tvTextoTienda.text = "Tienda Garritas · ${perfil.garritas}"

        binding.tvEtiquetaSinAnuncios.visibility =
            if (perfil.sin_anuncios) View.VISIBLE else View.GONE

        binding.tvMiembroDesde.text = perfil.miembro_desde?.let { fecha ->
            try {
                val entrada = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val salida = SimpleDateFormat("MMMM 'de' yyyy", Locale("es"))
                "Miembro desde ${salida.format(entrada.parse(fecha) ?: return@let "")}"
            } catch (e: Exception) {
                ""
            }
        } ?: ""
    }

    // ---------- Rachas de lectura ----------

    private fun cargarRacha() {
        lifecycleScope.launch {
            val estado = RachaManager.obtenerEstado(this@PerfilActivity)
            if (estado.success) {
                pintarRacha(estado)
            }
        }
    }

    private fun pintarRacha(estado: RachaEstado) {
        binding.tvRachaActual.text = estado.racha_actual.toString()
        binding.tvRachaMejor.text = "Mejor racha: ${estado.mejor_racha} días"

        // Estado de la racha: viva, en riesgo o rota.
        val hoyLeido = estado.ultima_actividad ==
            RachaManager.diaAString(RachaManager.hoyMedianoche())
        binding.tvRachaEstado.text = when {
            estado.racha_actual == 0 -> "Racha rota · lee hoy para empezar de nuevo"
            hoyLeido -> "¡Racha viva! Ya leíste hoy 🔥"
            else -> "En riesgo · lee hoy para no perderla"
        }

        // Tira semanal Lun-Dom: círculo en los días leídos.
        val diasViews = listOf(
            binding.tvDiaLun, binding.tvDiaMar, binding.tvDiaMie, binding.tvDiaJue,
            binding.tvDiaVie, binding.tvDiaSab, binding.tvDiaDom
        )
        diasViews.forEachIndexed { i, tv ->
            val leido = estado.dias_semana.getOrNull(i) == true
            if (leido) {
                tv.background = ContextCompat.getDrawable(this, R.drawable.bg_avatar_circulo)
                tv.setTextColor(ContextCompat.getColor(this, R.color.white))
            } else {
                tv.background = null
                tv.setTextColor(ContextCompat.getColor(this, R.color.muted))
            }
        }

        // Hitos 3 / 7 / 14 / 30.
        val hitos = estado.hitos.ifEmpty {
            RachaManager.hitosPara(estado.racha_actual, RachaManager.hitosReclamados(this))
        }
        hitos.forEach { hito ->
            val (texto, boton) = when (hito.dia) {
                3 -> binding.tvHito3Texto to binding.btnHito3
                7 -> binding.tvHito7Texto to binding.btnHito7
                14 -> binding.tvHito14Texto to binding.btnHito14
                else -> binding.tvHito30Texto to binding.btnHito30
            }
            val recompensa = if (hito.pro_horas > 0) "${hito.pro_horas} h de PRO"
                else "+${hito.garritas} garritas"
            texto.text = "Día ${hito.dia} · $recompensa"
            pintarBotonHito(boton, hito.dia, hito)
        }
    }

    private fun pintarBotonHito(boton: Button, dia: Int, hito: com.datgarscan.app.webapi.HitoRacha) {
        when {
            hito.reclamado -> {
                boton.text = "Reclamado ✓"
                boton.isEnabled = false
                boton.alpha = 0.5f
                boton.setOnClickListener(null)
            }
            !RachaManager.servidorDisponible -> {
                // El servidor aún no tiene api/rachas.php: no se puede
                // reclamar nada todavía, y no hacemos nada destructivo.
                boton.text = "Próximamente"
                boton.isEnabled = false
                boton.alpha = 0.5f
                boton.setOnClickListener(null)
            }
            hito.disponible -> {
                boton.text = "Reclamar"
                boton.isEnabled = true
                boton.alpha = 1f
                boton.setOnClickListener { reclamarHito(dia) }
            }
            else -> {
                boton.text = "Bloqueado"
                boton.isEnabled = false
                boton.alpha = 0.5f
                boton.setOnClickListener(null)
            }
        }
    }

    // La recompensa de un hito solo se cobra después de ver un anuncio
    // recompensado completo. La racha en sí no depende del anuncio: sigue
    // subiendo solo por leer. Esto vale para los 4 hitos (3, 7, 14 y 30).
    private fun reclamarHito(dia: Int) {
        if (!UnityAdsManager.rewardedListo()) {
            Toast.makeText(this, "Preparando el anuncio, espera unos segundos...", Toast.LENGTH_SHORT).show()
            UnityAdsManager.precargarRewarded()
            return
        }
        UnityAdsManager.mostrarRewarded(
            this,
            onCompletado = { cobrarHito(dia) },
            onFallido = {
                Toast.makeText(this, "No se pudo ver el anuncio. Inténtalo de nuevo.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun cobrarHito(dia: Int) {
        lifecycleScope.launch {
            val respuesta = RachaManager.reclamarHito(this@PerfilActivity, dia)
            Toast.makeText(
                this@PerfilActivity,
                respuesta.message ?: if (respuesta.success) "¡Recompensa reclamada!" else "No se pudo reclamar",
                Toast.LENGTH_SHORT
            ).show()
            if (respuesta.success) {
                cargarRacha()
                cargarPerfil()
            }
        }
    }

    private fun confirmarCerrarSesion() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Seguro que quieres cerrar sesión?")
            .setPositiveButton("Cerrar sesión") { _, _ ->
                SesionManager.cerrarSesion(this)
                com.datgarscan.app.tienda.SinAnunciosManager.limpiar(this)
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
