package com.datgarscan.app.tienda

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.datgarscan.app.databinding.ActivityTiendaBinding
import com.datgarscan.app.webapi.CodigoRequest
import com.datgarscan.app.webapi.GarritasEstado
import com.datgarscan.app.webapi.SesionManager
import com.datgarscan.app.webapi.WebApiClient
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class TiendaActivity : AppCompatActivity() {

    companion object {
        private const val SEGUNDOS_MINIMOS = 3

        fun crearIntent(context: Context): Intent = Intent(context, TiendaActivity::class.java)
    }

    private lateinit var binding: ActivityTiendaBinding
    private var anuncioStartApp: StartAppAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTiendaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!SesionManager.estaLogueado()) {
            Toast.makeText(this, "Inicia sesión para usar la tienda.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvVolver.setOnClickListener { finish() }
        binding.btnVerAnuncio.setOnClickListener { mostrarAnuncioRecompensado() }
        binding.btnBonusDiario.setOnClickListener { reclamarBonus() }
        binding.btnCanjearCodigo.setOnClickListener { pedirCodigo() }
        binding.btnSinAnuncios1Dia.setOnClickListener { comprarSinAnuncios(24) }
        binding.btnSinAnuncios1Semana.setOnClickListener { comprarSinAnuncios(168) }
        binding.btnSinAnuncios1Mes.setOnClickListener { comprarSinAnuncios(720) }

        prepararAnuncioRecompensado()
        cargarEstado()
    }

    // ---------- Estado ----------

    private fun cargarEstado() {
        lifecycleScope.launch {
            try {
                val estado = WebApiClient.get().estadoGarritas()
                pintarEstado(estado)
            } catch (e: Exception) {
                Toast.makeText(this@TiendaActivity, com.datgarscan.app.webapi.ErroresRed.mensajeAmable(e), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pintarEstado(estado: GarritasEstado) {
        binding.tvSaldo.text = estado.saldo.toString()

        // Guarda si tiene el beneficio activo, para que el resto de la app
        // sepa que no debe mostrarle anuncios.
        SinAnunciosManager.guardar(this, estado.sin_anuncios, estado.sin_anuncios_hasta)

        if (estado.sin_anuncios && estado.sin_anuncios_hasta != null) {
            binding.tvSinAnuncios.visibility = View.VISIBLE
            binding.tvSinAnuncios.text = "Sin anuncios hasta ${formatearFecha(estado.sin_anuncios_hasta)}"
        } else {
            binding.tvSinAnuncios.visibility = View.GONE
        }

        val quedan = estado.max_anuncios_dia - estado.anuncios_vistos_hoy
        anunciosQuedanHoy = quedan
        garritasPorAnuncio = estado.garritas_por_anuncio
        actualizarBotonAnuncio()

        binding.btnBonusDiario.text = if (estado.bonus_diario_disponible)
            "Bonus diario · +${estado.garritas_bonus_diario} garritas"
        else
            "Bonus diario ya reclamado hoy"
        binding.btnBonusDiario.isEnabled = estado.bonus_diario_disponible
        binding.btnBonusDiario.alpha = if (estado.bonus_diario_disponible) 1f else 0.5f
    }

    private fun formatearFecha(fecha: String): String {
        return try {
            val entrada = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val salida = SimpleDateFormat("d 'de' MMMM, HH:mm", Locale("es"))
            salida.format(entrada.parse(fecha) ?: return fecha)
        } catch (e: Exception) {
            fecha
        }
    }

    // ---------- Ganar garritas ----------

    private var anuncioListo = false
    private var intentosFallidos = 0
    private var anunciosQuedanHoy = 0
    private var garritasPorAnuncio = 5
    private var momentoMostrado = 0L

    private fun prepararAnuncioRecompensado() {
        anuncioListo = false
        val anuncio = StartAppAd(this)
        anuncio.loadAd(object : AdEventListener {
            override fun onReceiveAd(ad: Ad) {
                anuncioListo = true
                intentosFallidos = 0
                runOnUiThread { actualizarBotonAnuncio() }
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                anuncioListo = false
                intentosFallidos++
                runOnUiThread {
                    if (intentosFallidos >= 3) {
                        binding.btnVerAnuncio.visibility = View.GONE
                    } else {
                        binding.btnVerAnuncio.text = "No hay anuncios ahora. Toca para reintentar."
                        binding.btnVerAnuncio.isEnabled = true
                        binding.btnVerAnuncio.alpha = 1f
                    }
                }
            }
        })
        anuncioStartApp = anuncio
    }

    private fun mostrarAnuncioRecompensado() {
        val anuncio = anuncioStartApp

        if (anuncio == null || !anuncioListo) {
            Toast.makeText(this, "Preparando el anuncio, espera unos segundos...", Toast.LENGTH_SHORT).show()
            prepararAnuncioRecompensado()
            return
        }

        momentoMostrado = System.currentTimeMillis()

        anuncio.showAd(object : com.startapp.sdk.adsbase.adlisteners.AdDisplayListener {
            override fun adHidden(ad: Ad) {
                // Se otorgan las garritas solo si el anuncio estuvo abierto un
                // minimo de tiempo, para que no cuente si lo cierran al instante.
                val segundos = (System.currentTimeMillis() - momentoMostrado) / 1000
                if (segundos >= SEGUNDOS_MINIMOS) {
                    otorgarGarritasPorAnuncio()
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@TiendaActivity,
                            "Debes ver el anuncio completo para ganar garritas.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    prepararAnuncioRecompensado()
                }
            }

            override fun adDisplayed(ad: Ad) {}
            override fun adClicked(ad: Ad) {}
            override fun adNotDisplayed(ad: Ad) {
                runOnUiThread {
                    Toast.makeText(this@TiendaActivity, "No se pudo mostrar el anuncio.", Toast.LENGTH_SHORT).show()
                }
                prepararAnuncioRecompensado()
            }
        })
    }

    private fun otorgarGarritasPorAnuncio() {
        lifecycleScope.launch {
            try {
                val estado = WebApiClient.get().anuncioVisto()
                if (estado.success) {
                    pintarEstado(estado)
                    Toast.makeText(this@TiendaActivity, "+${estado.ganadas ?: 0} garritas", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@TiendaActivity, estado.message ?: "No se pudieron sumar las garritas.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TiendaActivity, "No se pudieron sumar las garritas.", Toast.LENGTH_SHORT).show()
            }
            prepararAnuncioRecompensado()
        }
    }

    private fun actualizarBotonAnuncio() {
        binding.btnVerAnuncio.text = when {
            anunciosQuedanHoy <= 0 -> "Ya viste todos los anuncios de hoy"
            !anuncioListo -> "Preparando anuncio..."
            else -> "Ver un anuncio · +$garritasPorAnuncio garritas  (te quedan $anunciosQuedanHoy hoy)"
        }
        binding.btnVerAnuncio.isEnabled = anunciosQuedanHoy > 0
        binding.btnVerAnuncio.alpha = if (anunciosQuedanHoy > 0 && anuncioListo) 1f else 0.5f
    }

    private fun reclamarBonus() {
        lifecycleScope.launch {
            try {
                val estado = WebApiClient.get().reclamarBonusDiario()
                if (estado.success) {
                    pintarEstado(estado)
                    Toast.makeText(this@TiendaActivity, "+${estado.ganadas ?: 0} garritas", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@TiendaActivity, estado.message ?: "No disponible.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TiendaActivity, com.datgarscan.app.webapi.ErroresRed.mensajeAmable(e), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pedirCodigo() {
        val campo = EditText(this)
        campo.hint = "DG-XXXXXXXX"
        campo.setPadding(40, 30, 40, 30)

        AlertDialog.Builder(this)
            .setTitle("Canjear código")
            .setMessage("Escribe el código que recibiste al apoyar en Patreon.")
            .setView(campo)
            .setPositiveButton("Canjear") { _, _ ->
                val codigo = campo.text.toString().trim()
                if (codigo.isNotBlank()) canjearCodigo(codigo)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun canjearCodigo(codigo: String) {
        lifecycleScope.launch {
            try {
                val estado = WebApiClient.get().canjearCodigo(req = CodigoRequest(codigo))
                if (estado.success) {
                    pintarEstado(estado)
                    Toast.makeText(this@TiendaActivity, "+${estado.ganadas ?: 0} garritas", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@TiendaActivity, estado.message ?: "Código no válido.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TiendaActivity, com.datgarscan.app.webapi.ErroresRed.mensajeAmable(e), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- Canjear garritas ----------

    private fun comprarSinAnuncios(horas: Int) {
        val descripcion = when (horas) {
            24 -> "1 día"
            168 -> "1 semana"
            else -> "1 mes"
        }

        AlertDialog.Builder(this)
            .setTitle("Quitar anuncios")
            .setMessage("¿Canjear tus garritas por $descripcion sin anuncios?")
            .setPositiveButton("Canjear") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val estado = WebApiClient.get().comprarSinAnuncios(horas = horas)
                        if (estado.success) {
                            pintarEstado(estado)
                            AlertDialog.Builder(this@TiendaActivity)
                                .setTitle("Listo")
                                .setMessage("Ya tienes $descripcion sin anuncios.\n\nVuelve al inicio para que se aplique.")
                                .setPositiveButton("Entendido", null)
                                .show()
                        } else {
                            Toast.makeText(this@TiendaActivity, estado.message ?: "No se pudo canjear.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@TiendaActivity, com.datgarscan.app.webapi.ErroresRed.mensajeAmable(e), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
