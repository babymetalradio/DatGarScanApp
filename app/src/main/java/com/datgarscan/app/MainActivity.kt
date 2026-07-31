package com.datgarscan.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.datgarscan.app.databinding.ActivityMainBinding
import com.datgarscan.app.detalle.SerieDetalleActivity
import com.datgarscan.app.lector.LectorActivity
import com.datgarscan.app.login.LoginActivity
import com.datgarscan.app.webapi.MangaResumen
import com.datgarscan.app.webapi.SesionManager
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MangaAdapter
    private var listoParaOcultarSplash = false

    private var catalogoCompleto: List<MangaResumen> = emptyList()
    private var generoSeleccionado: String? = null
    private val handlerBusqueda = android.os.Handler(android.os.Looper.getMainLooper())
    private var runnableBusqueda: Runnable? = null

    private val loginLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { actualizarEstadoSesion(); cargarContinuarLeyendo() }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !listoParaOcultarSplash }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SesionManager.cargar(this)

        adapter = MangaAdapter { manga ->
            startActivity(SerieDetalleActivity.crearIntent(this, manga.slug))
        }
        binding.rvSeries.layoutManager = GridLayoutManager(this, 2)
        binding.rvSeries.adapter = adapter

        binding.tvActualizar.setOnClickListener { cargarCatalogo() }
        binding.tvSesion.setOnClickListener {
            if (SesionManager.estaLogueado()) {
                mostrarMenuUsuario()
            } else {
                loginLauncher.launch(LoginActivity.crearIntent(this))
            }
        }
        actualizarEstadoSesion()

        binding.etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                runnableBusqueda?.let { handlerBusqueda.removeCallbacks(it) }
                val nuevo = Runnable { aplicarFiltros() }
                runnableBusqueda = nuevo
                handlerBusqueda.postDelayed(nuevo, 300)
            }
        })

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"
        binding.tvSyncVersion.text = "v${BuildConfig.VERSION_NAME}"

        binding.tvSyncEstado.text = "Preparando conexión..."
        binding.overlaySync.visibility = View.VISIBLE
        com.datgarscan.app.webapi.ChallengeResolver.ejecutar(binding.webViewChallenge) {
            runOnUiThread {
                listoParaOcultarSplash = true
                com.datgarscan.app.webapi.WebApiClient.configurarGlide(applicationContext)
                cargarCatalogo()
                cargarContinuarLeyendo()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.datgarscan.app.webapi.ChallengeResolver.resuelto) {
            cargarContinuarLeyendo()
        }
    }

    private fun cargarContinuarLeyendo() {
        if (!SesionManager.estaLogueado()) {
            binding.cardContinuar.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().listarHistorial()
                val ultimo = respuesta.data.firstOrNull()
                if (!respuesta.success || ultimo == null) {
                    binding.cardContinuar.visibility = View.GONE
                    return@launch
                }

                val numero = if (ultimo.chapter_number == ultimo.chapter_number.toLong().toDouble())
                    ultimo.chapter_number.toLong().toString() else ultimo.chapter_number.toString()

                binding.tvContinuarTitulo.text = "Continuando: ${ultimo.title}"
                binding.tvContinuarCapitulo.text = "Cap $numero, pág. ${ultimo.page_number}"
                com.bumptech.glide.Glide.with(this@MainActivity).load(ultimo.cover_url).into(binding.ivContinuarPortada)

                binding.cardContinuar.visibility = View.VISIBLE
                binding.cardContinuar.setOnClickListener {
                    val paginaInicial = (ultimo.page_number - 1).coerceAtLeast(0)
                    startActivity(LectorActivity.crearIntent(this@MainActivity, ultimo.chapter_id, paginaInicial))
                }

            } catch (e: Exception) {
                binding.cardContinuar.visibility = View.GONE
            }
        }
    }

    private fun actualizarEstadoSesion() {
        binding.tvSesion.text = if (SesionManager.estaLogueado())
            SesionManager.usernameEnMemoria ?: "Iniciar sesión"
        else
            "Iniciar sesión"
    }

    private fun mostrarMenuUsuario() {
        val popup = android.widget.PopupMenu(this, binding.tvSesion)
        popup.menu.add("Favoritos")
        popup.menu.add("Historial")
        popup.menu.add("Cerrar sesión")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Favoritos" -> startActivity(com.datgarscan.app.favoritos.FavoritosActivity.crearIntent(this))
                "Historial" -> startActivity(com.datgarscan.app.historial.HistorialActivity.crearIntent(this))
                "Cerrar sesión" -> {
                    SesionManager.cerrarSesion(this)
                    actualizarEstadoSesion()
                }
            }
            true
        }
        popup.show()
    }

    private fun cargarCatalogo() {
        binding.overlaySync.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        binding.tvSyncEstado.text = "Cargando catálogo..."

        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().listarMangas()
                binding.overlaySync.visibility = View.GONE

                if (!respuesta.success) {
                    mostrarError(respuesta.message ?: "Error desconocido del servidor.")
                    return@launch
                }

                catalogoCompleto = respuesta.data
                armarChipsDeGenero()
                aplicarFiltros()

            } catch (e: Exception) {
                Log.e("MainActivity", "Error cargando catálogo", e)
                binding.overlaySync.visibility = View.GONE

                if (e is com.google.gson.stream.MalformedJsonException) {
                    val crudo = obtenerRespuestaCruda("api/mangas.php")
                    mostrarError("El servidor no devolvió JSON válido. Respuesta real:\n\n${crudo.take(500)}")
                } else {
                    mostrarError("Error de conexión: ${e.javaClass.simpleName} — ${e.message}")
                }
            }
        }
    }

    /** Vuelve a pedir la misma URL, esta vez sin exigir que sea JSON, para ver qué llegó de verdad. */
    private suspend fun obtenerRespuestaCruda(ruta: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "${com.datgarscan.app.webapi.WebApiClient.SITE_URL}$ruta"
            val cliente = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            cliente.newCall(request).execute().use { it.body?.string() ?: "(sin cuerpo)" }
        } catch (e: Exception) {
            "(no se pudo obtener: ${e.message})"
        }
    }

    /** Junta los géneros distintos de todo el catálogo y arma los chips para filtrar. */
    private fun armarChipsDeGenero() {
        val generos = catalogoCompleto.flatMap { it.genres }.distinct().sorted()
        binding.contenedorGeneros.removeAllViews()

        if (generos.isEmpty()) {
            binding.scrollGeneros.visibility = View.GONE
            return
        }
        binding.scrollGeneros.visibility = View.VISIBLE

        fun crearChip(texto: String, generoAsociado: String?): TextView {
            val chip = TextView(this)
            chip.text = texto
            chip.textSize = 12f
            chip.setPadding(28, 14, 28, 14)
            chip.gravity = Gravity.CENTER
            val margins = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            margins.marginEnd = 16
            chip.layoutParams = margins
            chip.setOnClickListener {
                generoSeleccionado = generoAsociado
                armarChipsDeGenero()
                aplicarFiltros()
            }
            val activo = generoAsociado == generoSeleccionado
            chip.setBackgroundResource(if (activo) R.drawable.bg_boton_gradiente else R.drawable.bg_chip)
            chip.setTextColor(resources.getColor(if (activo) R.color.white else R.color.muted, theme))
            return chip
        }

        binding.contenedorGeneros.addView(crearChip("Todos", null))
        generos.forEach { g -> binding.contenedorGeneros.addView(crearChip(g, g)) }
    }

    /** Aplica búsqueda + género sobre el catálogo ya cargado, sin pedir nada al servidor de nuevo. */
    private fun aplicarFiltros() {
        val texto = binding.etBuscar.text?.toString()?.trim()?.lowercase() ?: ""

        val filtrado = catalogoCompleto.filter { manga ->
            val pasaTexto = texto.isEmpty() || manga.title.lowercase().contains(texto)
            val pasaGenero = generoSeleccionado == null || manga.genres.contains(generoSeleccionado)
            pasaTexto && pasaGenero
        }

        mostrarMangas(filtrado)
    }

    private fun mostrarMangas(mangas: List<MangaResumen>) {
        if (mangas.isEmpty()) {
            mostrarError(if (catalogoCompleto.isEmpty()) "No hay mangas publicados todavía." else "Sin resultados con ese filtro.")
            return
        }
        binding.tvError.visibility = View.GONE
        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME} · ${mangas.size} series"
        adapter.actualizar(mangas)
    }

    private fun mostrarError(mensaje: String) {
        binding.tvError.text = mensaje
        binding.tvError.visibility = View.VISIBLE
        adapter.actualizar(emptyList())
    }
}
