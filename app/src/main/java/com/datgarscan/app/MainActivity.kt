package com.datgarscan.app

import android.content.Context
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

    companion object {
        const val EXTRA_ABRIR_MANGA_SLUG = "extra_abrir_manga_slug"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MangaAdapter

    private var catalogoCompleto: List<MangaResumen> = emptyList()
    private var generoSeleccionado: String? = null
    private val handlerBusqueda = android.os.Handler(android.os.Looper.getMainLooper())
    private var runnableBusqueda: Runnable? = null

    private val loginLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        actualizarEstadoSesion()
        cargarContinuarLeyendo()
        com.datgarscan.app.notificaciones.NotificacionesManager.registrarSiHaySesion(this)
        sincronizarGarritas()
    }

    private val permisoNotificacionesLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* si lo niega, simplemente no le llegan notificaciones - no pasa nada */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // El splash nativo solo cubre el arranque instantaneo; enseguida se
        // quita para dar paso a la pantalla de carga propia (con logo,
        // indicador, version y creditos), que es la que se ve mientras se
        // resuelve la conexion y se trae el catalogo.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.overlaySync.visibility = View.VISIBLE
        binding.tvSyncVersion.text = "v${BuildConfig.VERSION_NAME}"

        SesionManager.cargar(this)
        configurarMenuLateral()
        try { com.datgarscan.app.notificaciones.crearCanalSiHaceFalta(this) } catch (e: Throwable) {}
        try { pedirPermisoNotificacionesSiHaceFalta() } catch (e: Throwable) {}

        adapter = MangaAdapter { manga ->
            startActivity(SerieDetalleActivity.crearIntent(this, manga.slug))
        }
        binding.rvSeries.layoutManager = GridLayoutManager(this, 2)
        binding.rvSeries.adapter = adapter

        com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerAds)

        binding.tvActualizar.setOnClickListener { recargarTodo() }
        binding.tvReintentar.setOnClickListener { recargarTodo() }
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

        // Al confirmar la busqueda desde el teclado, se cierra el menu para
        // que se vean los resultados en el catalogo.
        binding.etBuscar.setOnEditorActionListener { _, _, _ ->
            aplicarFiltros()
            cerrarMenu()
            ocultarTeclado()
            true
        }

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"
        binding.tvSyncVersion.text = "v${BuildConfig.VERSION_NAME}"

        val cache = com.datgarscan.app.webapi.CatalogoCache.cargar(this)
        if (cache != null) {
            catalogoCompleto = cache
            armarGenerosMenu()
            aplicarFiltros()
        }

        binding.tvSyncEstado.text = "Preparando conexión..."
        binding.overlaySync.visibility = View.VISIBLE
        com.datgarscan.app.webapi.ChallengeResolver.ejecutar(binding.webViewChallenge) {
            runOnUiThread {
                // Cada paso va protegido: en dispositivos limitados (sin Google
                // Play Services, por ejemplo) alguno puede fallar, y eso no debe
                // impedir que la app abra ni tumbarla.
                try { com.datgarscan.app.webapi.WebApiClient.configurarGlide(applicationContext) } catch (e: Throwable) {}
                try { cargarCatalogo() } catch (e: Throwable) {}
                try { cargarContinuarLeyendo() } catch (e: Throwable) {}
                try { com.datgarscan.app.notificaciones.NotificacionesManager.registrarSiHaySesion(this@MainActivity) } catch (e: Throwable) {}
                try { revisarPopupRemoto() } catch (e: Throwable) {}
                try { sincronizarGarritas() } catch (e: Throwable) {}

                val slugDesdeNotificacion = intent.getStringExtra(EXTRA_ABRIR_MANGA_SLUG)
                if (!slugDesdeNotificacion.isNullOrBlank()) {
                    try {
                        startActivity(SerieDetalleActivity.crearIntent(this@MainActivity, slugDesdeNotificacion))
                    } catch (e: Throwable) {}
                }
            }
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            cerrarMenu()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.datgarscan.app.webapi.ChallengeResolver.resuelto) {
            cargarContinuarLeyendo()
            // Al volver de la tienda, aplica de inmediato el "sin anuncios"
            // recien canjeado, sin necesidad de cerrar y abrir la app.
            sincronizarGarritas()
            com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerAds)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (com.datgarscan.app.webapi.ChallengeResolver.resuelto) {
            val slugDesdeNotificacion = intent.getStringExtra(EXTRA_ABRIR_MANGA_SLUG)
            if (!slugDesdeNotificacion.isNullOrBlank()) {
                startActivity(SerieDetalleActivity.crearIntent(this, slugDesdeNotificacion))
            }
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

    /**
     * Consulta si hay un aviso configurado desde el panel del sitio. Solo lo
     * muestra si es uno que este dispositivo no ha visto todavia (se compara
     * la version, que sube cada vez que se edita el aviso en el panel).
     */
    /**
     * Pregunta al servidor si esta cuenta tiene tiempo sin anuncios activo y lo
     * guarda en el telefono. Es importante hacerlo al abrir la app y al iniciar
     * sesion: el beneficio pertenece a la cuenta, no al dispositivo, asi que al
     * cambiar de usuario hay que volver a consultarlo.
     */
    /**
     * Recarga catalogo, progreso de lectura y estado de garritas de una vez.
     * Sirve para que, tras canjear tiempo sin anuncios en la tienda, los
     * banners desaparezcan sin tener que cerrar la app por completo.
     */
    private fun recargarTodo() {
        cargarCatalogo()
        cargarContinuarLeyendo()
        lifecycleScope.launch {
            if (SesionManager.estaLogueado()) {
                try {
                    val estado = WebApiClient.get().estadoGarritas()
                    if (estado.success) {
                        com.datgarscan.app.tienda.SinAnunciosManager.guardar(
                            this@MainActivity, estado.sin_anuncios, estado.sin_anuncios_hasta
                        )
                    }
                } catch (e: Exception) { /* seguimos con lo que hubiera */ }
            }
            com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(
                this@MainActivity, binding.bannerAds
            )
        }
    }

    private fun sincronizarGarritas() {
        if (!SesionManager.estaLogueado()) {
            com.datgarscan.app.tienda.SinAnunciosManager.limpiar(this)
            return
        }
        lifecycleScope.launch {
            try {
                val estado = WebApiClient.get().estadoGarritas()
                if (estado.success) {
                    com.datgarscan.app.tienda.SinAnunciosManager.guardar(
                        this@MainActivity,
                        estado.sin_anuncios,
                        estado.sin_anuncios_hasta
                    )
                }
            } catch (e: Exception) {
                // Si falla, se mantiene lo que hubiera guardado de antes
            }
        }
    }

    private fun revisarPopupRemoto() {
        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().obtenerPopup()
                if (!respuesta.mostrar || respuesta.data == null) return@launch

                val popup = respuesta.data
                val prefs = getSharedPreferences("datgar_popup", Context.MODE_PRIVATE)
                val ultimaVista = prefs.getInt("ultima_version_vista", 0)
                if (popup.version <= ultimaVista) return@launch

                startActivity(
                    com.datgarscan.app.popup.PopupActivity.crearIntent(
                        this@MainActivity,
                        popup.titulo,
                        popup.mensaje,
                        popup.texto_boton,
                        popup.url_boton
                    )
                )
                prefs.edit().putInt("ultima_version_vista", popup.version).apply()

            } catch (e: Exception) {
                // Si falla, simplemente no se muestra nada - no es critico
            }
        }
    }

    private fun pedirPermisoNotificacionesSiHaceFalta() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val yaConcedido = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!yaConcedido) {
            permisoNotificacionesLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun actualizarEstadoSesion() {
        binding.tvMenuUsuario.text = if (SesionManager.estaLogueado())
            SesionManager.usernameEnMemoria ?: "" else "Sin sesión iniciada"

        binding.tvSesion.text = if (SesionManager.estaLogueado())
            SesionManager.usernameEnMemoria ?: "Iniciar sesión"
        else
            "Iniciar sesión"
    }

    private fun mostrarMenuUsuario() {
        val popup = android.widget.PopupMenu(this, binding.tvSesion)
        popup.menu.add("Mi perfil")
        popup.menu.add("Tienda")
        popup.menu.add("Favoritos")
        popup.menu.add("Historial")
        popup.menu.add("Cerrar sesión")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Mi perfil" -> startActivity(com.datgarscan.app.perfil.PerfilActivity.crearIntent(this))
                "Tienda" -> startActivity(com.datgarscan.app.tienda.TiendaActivity.crearIntent(this))
                "Favoritos" -> startActivity(com.datgarscan.app.favoritos.FavoritosActivity.crearIntent(this))
                "Historial" -> startActivity(com.datgarscan.app.historial.HistorialActivity.crearIntent(this))
                "Cerrar sesión" -> {
                    SesionManager.cerrarSesion(this)
                    com.datgarscan.app.tienda.SinAnunciosManager.limpiar(this)
                    actualizarEstadoSesion()
                }
            }
            true
        }
        popup.show()
    }

    private fun cargarCatalogo() {
        val yaHayDatos = catalogoCompleto.isNotEmpty()
        if (!yaHayDatos) {
            binding.overlaySync.visibility = View.VISIBLE
            binding.tvSyncEstado.text = "Cargando catálogo..."
        }
        binding.contenedorError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().listarMangas()
                binding.overlaySync.visibility = View.GONE

                if (!respuesta.success) {
                    if (!yaHayDatos) mostrarError(respuesta.message ?: "Error desconocido del servidor.")
                    return@launch
                }

                catalogoCompleto = respuesta.data
                com.datgarscan.app.webapi.CatalogoCache.guardar(this@MainActivity, respuesta.data)
                armarGenerosMenu()
                aplicarFiltros()

            } catch (e: Exception) {
                Log.e("MainActivity", "Error cargando catálogo", e)
                binding.overlaySync.visibility = View.GONE

                if (yaHayDatos) {
                    // Ya se ve algo en pantalla (del cache) — no interrumpimos con un error.
                    return@launch
                }

                if (e is com.google.gson.stream.MalformedJsonException) {
                    val crudo = obtenerRespuestaCruda("api/mangas.php")
                    mostrarError("El servidor no devolvió JSON válido. Respuesta real:\n\n${crudo.take(500)}")
                } else {
                    mostrarError(com.datgarscan.app.webapi.ErroresRed.mensajeAmable(e))
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
    /** Aplica búsqueda + género sobre el catálogo ya cargado, sin pedir nada al servidor de nuevo. */
    private fun aplicarFiltros() {
        val texto = binding.etBuscar.text?.toString()?.trim()?.lowercase() ?: ""

        val filtrado = catalogoCompleto.filter { manga ->
            val pasaTexto = texto.isEmpty() || manga.title.lowercase().contains(texto)
            val pasaGenero = generoSeleccionado == null || manga.genres.contains(generoSeleccionado)
            pasaTexto && pasaGenero
        }

        val ordenado = when (ordenActual) {
            Orden.ALFABETICO -> filtrado.sortedBy { it.title.lowercase() }
            Orden.MAS_VISTOS -> filtrado.sortedByDescending { it.views }
            Orden.RECIENTES -> filtrado.sortedByDescending { it.last_chapter_at ?: "" }
        }

        mostrarMangas(ordenado)
    }

    // ---------- Menu lateral ----------

    private enum class Orden { RECIENTES, ALFABETICO, MAS_VISTOS }

    private var ordenActual = Orden.RECIENTES

    private fun configurarMenuLateral() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        binding.menuPerfil.setOnClickListener {
            cerrarMenu()
            if (SesionManager.estaLogueado()) {
                startActivity(com.datgarscan.app.perfil.PerfilActivity.crearIntent(this))
            } else {
                loginLauncher.launch(LoginActivity.crearIntent(this))
            }
        }
        binding.menuTienda.setOnClickListener {
            cerrarMenu()
            if (SesionManager.estaLogueado()) {
                startActivity(com.datgarscan.app.tienda.TiendaActivity.crearIntent(this))
            } else {
                loginLauncher.launch(LoginActivity.crearIntent(this))
            }
        }
        binding.menuFavoritos.setOnClickListener {
            cerrarMenu()
            startActivity(com.datgarscan.app.favoritos.FavoritosActivity.crearIntent(this))
        }
        binding.menuHistorial.setOnClickListener {
            cerrarMenu()
            startActivity(com.datgarscan.app.historial.HistorialActivity.crearIntent(this))
        }

        armarOpcionesOrden()
    }

    private fun ocultarTeclado() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.etBuscar.windowToken, 0)
        } catch (e: Throwable) { /* no es critico */ }
    }

    private fun cerrarMenu() {
        binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
    }

    private fun armarOpcionesOrden() {
        binding.grupoOrden.removeAllViews()
        val opciones = listOf(
            "Recientes" to Orden.RECIENTES,
            "Alfabético" to Orden.ALFABETICO,
            "Más vistos" to Orden.MAS_VISTOS
        )
        opciones.forEach { (texto, valor) ->
            binding.grupoOrden.addView(crearOpcionMenu(texto, valor == ordenActual) {
                ordenActual = valor
                armarOpcionesOrden()
                aplicarFiltros()
                cerrarMenu()
            })
        }
    }

    private fun armarGenerosMenu() {
        binding.grupoGenerosMenu.removeAllViews()

        binding.grupoGenerosMenu.addView(crearOpcionMenu("Todos", generoSeleccionado == null) {
            generoSeleccionado = null
            armarGenerosMenu()
            aplicarFiltros()
            cerrarMenu()
        })

        catalogoCompleto.flatMap { it.genres }.distinct().sorted().forEach { genero ->
            binding.grupoGenerosMenu.addView(crearOpcionMenu(genero, generoSeleccionado == genero) {
                generoSeleccionado = genero
                armarGenerosMenu()
                aplicarFiltros()
                cerrarMenu()
            })
        }
    }

    private fun crearOpcionMenu(texto: String, activo: Boolean, alTocar: () -> Unit): View {
        val tv = android.widget.TextView(this)
        tv.text = texto
        tv.textSize = 14f
        tv.setPadding(24, 26, 24, 26)
        tv.setTextColor(resources.getColor(if (activo) R.color.accent else R.color.white, theme))
        if (activo) tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.setOnClickListener { alTocar() }
        return tv
    }

    private fun mostrarMangas(mangas: List<MangaResumen>) {
        if (mangas.isEmpty()) {
            mostrarError(if (catalogoCompleto.isEmpty()) "No hay mangas publicados todavía." else "Sin resultados con ese filtro.")
            return
        }
        binding.contenedorError.visibility = View.GONE
        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME} · ${mangas.size} series"
        adapter.actualizar(mangas)
    }

    private fun mostrarError(mensaje: String) {
        binding.tvError.text = mensaje
        binding.contenedorError.visibility = View.VISIBLE
        adapter.actualizar(emptyList())
    }
}
