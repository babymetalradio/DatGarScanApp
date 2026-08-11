package com.datgarscan.app.lector

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.datgarscan.app.R
import com.datgarscan.app.databinding.ActivityLectorBinding
import com.datgarscan.app.webapi.HistorialGuardarRequest
import com.datgarscan.app.webapi.SesionManager
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.launch

class LectorActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_CHAPTER_ID = "extra_chapter_id"
        private const val EXTRA_PAGINA_INICIAL = "extra_pagina_inicial"
        private const val PREFS = "datgar_lector_prefs"
        private const val KEY_MODO = "modo_lectura"
        private const val KEY_DIRECCION = "direccion_lectura"
        private const val KEY_FONDO = "fondo_lectura"

        fun crearIntent(context: Context, chapterId: Int, paginaInicial: Int = 0): Intent {
            return Intent(context, LectorActivity::class.java)
                .putExtra(EXTRA_CHAPTER_ID, chapterId)
                .putExtra(EXTRA_PAGINA_INICIAL, paginaInicial)
        }
    }

    private enum class ModoLectura { PAGINA, DOBLE, TIRA }
    private enum class Direccion { RTL, LTR }
    private enum class Fondo { NEGRO, OSCURO, BLANCO }

    private lateinit var binding: ActivityLectorBinding
    private var mangaId: Int = -1
    private var chapterId: Int = -1
    private var prevChapterId: Int? = null
    private var nextChapterId: Int? = null
    private var paginas: List<String> = emptyList()
    private var modoActual = ModoLectura.PAGINA
    private var direccionActual = Direccion.RTL
    private var fondoActual = Fondo.NEGRO
    private val handlerGuardado = android.os.Handler(android.os.Looper.getMainLooper())
    private var runnableGuardado: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLectorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chapterId = intent.getIntExtra(EXTRA_CHAPTER_ID, -1)
        val paginaInicial = intent.getIntExtra(EXTRA_PAGINA_INICIAL, 0)
        if (chapterId <= 0) {
            Toast.makeText(this, "Capitulo invalido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Bloquea capturas y grabacion de pantalla dentro del lector, para
        // dificultar que se copien las traducciones.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        // Anuncio de pantalla completa cada cierto numero de capitulos abiertos
        com.datgarscan.app.ads.AnunciosManager.registrarCapituloAbierto(this)
        com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerLector)

        cargarPreferencias()

        binding.tvVolver.setOnClickListener { salirDelLector() }
        binding.tvModoLectura.setOnClickListener {
            binding.panelAjustes.visibility =
                if (binding.panelAjustes.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.tvCapAnterior.setOnClickListener {
            prevChapterId?.let { id -> irAOtroCapitulo(id) }
        }
        binding.tvCapSiguiente.setOnClickListener {
            nextChapterId?.let { id -> irAOtroCapitulo(id) }
        }

        binding.viewPagerPaginas.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val posicionReal = posicionRealDesdeAdapter(position)
                actualizarContador(posicionReal)
                programarGuardarProgreso(posicionReal)
                precargarSiguiente(posicionReal)
            }
        })

        armarPanelAjustes()
        aplicarFondo()
        cargarPaginas(chapterId, paginaInicial)
    }

    private var barrasVisibles = true

    private fun alternarBarras() {
        barrasVisibles = !barrasVisibles
        val visibilidad = if (barrasVisibles) View.VISIBLE else View.GONE
        binding.topBar.visibility = visibilidad
        binding.bottomBar.visibility = visibilidad
        binding.bannerLector.visibility = visibilidad
        if (paginas.isNotEmpty()) {
            binding.tvContador.visibility = visibilidad
        }
        if (!barrasVisibles) {
            binding.panelAjustes.visibility = View.GONE
        }
    }

    private fun precargarSiguiente(posicionReal: Int) {
        val siguiente = paginas.getOrNull(posicionReal + 1) ?: return
        com.bumptech.glide.Glide.with(this).load(siguiente).preload()
    }

    private fun cargarPreferencias() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        modoActual = when (prefs.getString(KEY_MODO, null)) {
            "DOBLE" -> ModoLectura.DOBLE
            "TIRA" -> ModoLectura.TIRA
            else -> ModoLectura.PAGINA
        }
        direccionActual = if (prefs.getString(KEY_DIRECCION, null) == "LTR") Direccion.LTR else Direccion.RTL
        fondoActual = when (prefs.getString(KEY_FONDO, null)) {
            "OSCURO" -> Fondo.OSCURO
            "BLANCO" -> Fondo.BLANCO
            else -> Fondo.NEGRO
        }
    }

    private fun guardarPreferencias() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODO, modoActual.name)
            .putString(KEY_DIRECCION, direccionActual.name)
            .putString(KEY_FONDO, fondoActual.name)
            .apply()
    }

    // ---------- Panel de ajustes ----------

    private fun armarPanelAjustes() {
        armarGrupoOpciones(binding.grupoModo, listOf(
            "Una pagina" to ModoLectura.PAGINA,
            "Doble pagina" to ModoLectura.DOBLE,
            "Tira vertical" to ModoLectura.TIRA
        ), modoActual) { seleccionado -> cambiarModo(seleccionado) }

        armarGrupoOpciones(binding.grupoDireccion, listOf(
            "Derecha a izq (manga)" to Direccion.RTL,
            "Izq a derecha" to Direccion.LTR
        ), direccionActual) { seleccionado -> cambiarDireccion(seleccionado) }

        armarGrupoOpciones(binding.grupoFondo, listOf(
            "Negro" to Fondo.NEGRO,
            "Oscuro" to Fondo.OSCURO,
            "Blanco" to Fondo.BLANCO
        ), fondoActual) { seleccionado -> cambiarFondo(seleccionado) }
    }

    private fun <T> armarGrupoOpciones(
        contenedor: android.widget.LinearLayout,
        opciones: List<Pair<String, T>>,
        actual: T,
        onSeleccion: (T) -> Unit
    ) {
        contenedor.removeAllViews()
        opciones.forEach { (texto, valor) ->
            val chip = TextView(this)
            chip.text = texto
            chip.textSize = 11f
            chip.setPadding(20, 14, 20, 14)
            chip.gravity = Gravity.CENTER
            val params = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            params.marginEnd = 8
            chip.layoutParams = params
            val activo = valor == actual
            chip.setBackgroundResource(if (activo) R.drawable.bg_boton_gradiente else R.drawable.bg_chip)
            chip.setTextColor(resources.getColor(if (activo) R.color.white else R.color.muted, theme))
            chip.setOnClickListener {
                onSeleccion(valor)
                armarPanelAjustes()
            }
            contenedor.addView(chip)
        }
    }

    // ---------- Cambios de modo/direccion/fondo ----------

    private fun cambiarModo(nuevo: ModoLectura) {
        if (nuevo == modoActual) return
        val posicionActual = obtenerPosicionActual()
        modoActual = nuevo
        guardarPreferencias()
        aplicarModoALaVista()
        if (paginas.isNotEmpty()) irAPagina(posicionActual)
    }

    private fun cambiarDireccion(nueva: Direccion) {
        if (nueva == direccionActual) return
        val posicionActual = obtenerPosicionActual()
        direccionActual = nueva
        guardarPreferencias()
        aplicarDireccion()
        reconstruirAdaptadores()
        if (paginas.isNotEmpty()) irAPagina(posicionActual)
    }

    private fun cambiarFondo(nuevo: Fondo) {
        fondoActual = nuevo
        guardarPreferencias()
        aplicarFondo()
    }

    private fun aplicarFondo() {
        val color = when (fondoActual) {
            Fondo.NEGRO -> Color.BLACK
            Fondo.OSCURO -> Color.parseColor("#1A1520")
            Fondo.BLANCO -> Color.WHITE
        }
        binding.rootLector.setBackgroundColor(color)
    }

    private fun aplicarDireccion() {
        val layoutDir = if (direccionActual == Direccion.RTL) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        binding.viewPagerPaginas.layoutDirection = layoutDir
    }

    private fun obtenerPosicionActual(): Int {
        return when (modoActual) {
            ModoLectura.PAGINA -> posicionRealDesdeAdapter(binding.viewPagerPaginas.currentItem)
            ModoLectura.DOBLE -> posicionRealDesdeAdapter(binding.viewPagerPaginas.currentItem * 2)
            ModoLectura.TIRA -> (binding.rvTira.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
        }
    }

    /** En modo doble, la posicion del adapter (el par) no es la pagina real; en el resto si. */
    private fun posicionRealDesdeAdapter(posicionAdapter: Int): Int {
        return if (modoActual == ModoLectura.DOBLE) posicionAdapter * 2 else posicionAdapter
    }

    private fun aplicarModoALaVista() {
        binding.viewPagerPaginas.visibility = if (modoActual == ModoLectura.TIRA) View.GONE else View.VISIBLE
        binding.rvTira.visibility = if (modoActual == ModoLectura.TIRA) View.VISIBLE else View.GONE
        reconstruirAdaptadores()
    }

    private fun reconstruirAdaptadores() {
        if (paginas.isEmpty()) return
        when (modoActual) {
            ModoLectura.PAGINA -> {
                binding.viewPagerPaginas.adapter = PaginaAdapter(
                    paginas,
                    onTap = { alternarBarras() },
                    onZoomChanged = { ampliada -> binding.viewPagerPaginas.isUserInputEnabled = !ampliada }
                )
            }
            ModoLectura.DOBLE -> {
                binding.viewPagerPaginas.adapter = PaginaDobleAdapter(
                    armarParesDobles(),
                    onTap = { alternarBarras() }
                )
            }
            ModoLectura.TIRA -> {
                binding.rvTira.adapter = TiraAdapter(paginas, onTap = { alternarBarras() })
            }
        }
    }

    private fun armarParesDobles(): List<Pair<String, String?>> {
        val pares = mutableListOf<Pair<String, String?>>()
        var i = 0
        while (i < paginas.size) {
            val a = paginas[i]
            val b = paginas.getOrNull(i + 1)
            // En manga (RTL) la pagina de numero menor va a la derecha.
            pares.add(if (direccionActual == Direccion.RTL) (b ?: a) to (if (b != null) a else null) else a to b)
            i += 2
        }
        return pares
    }

    private fun irAPagina(posicionReal: Int) {
        if (paginas.isEmpty()) return
        val posReal = posicionReal.coerceIn(0, paginas.size - 1)
        when (modoActual) {
            ModoLectura.PAGINA -> binding.viewPagerPaginas.setCurrentItem(posReal, false)
            ModoLectura.DOBLE -> binding.viewPagerPaginas.setCurrentItem(posReal / 2, false)
            ModoLectura.TIRA -> (binding.rvTira.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(posReal, 0)
        }
        actualizarContador(posReal)
    }

    private fun irAOtroCapitulo(nuevoChapterId: Int) {
        finish()
        startActivity(crearIntent(this, nuevoChapterId, 0))
    }

    private fun actualizarContador(posicionReal: Int) {
        if (paginas.isEmpty()) return
        binding.tvContador.text = "${posicionReal + 1} / ${paginas.size}"
    }

    private fun cargarPaginas(chapterId: Int, paginaInicial: Int) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().obtenerCapitulo(chapterId)
                binding.progressBar.visibility = View.GONE

                if (!respuesta.success || respuesta.data == null) {
                    mostrarError(respuesta.message ?: "No se pudo cargar el capitulo.")
                    return@launch
                }

                val capitulo = respuesta.data
                mangaId = capitulo.manga_id
                prevChapterId = capitulo.prev_chapter_id
                nextChapterId = capitulo.next_chapter_id
                binding.tvTituloCapitulo.text = "${capitulo.manga_title} - Cap ${capitulo.chapter_number}"

                binding.tvCapAnterior.alpha = if (prevChapterId != null) 1f else 0.4f
                binding.tvCapSiguiente.alpha = if (nextChapterId != null) 1f else 0.4f

                if (capitulo.tiene_sorpresa) {
                    binding.tvSorpresa.visibility = View.VISIBLE
                    binding.tvSorpresa.setOnClickListener { reclamarSorpresa(chapterId) }
                } else {
                    binding.tvSorpresa.visibility = View.GONE
                }

                if (capitulo.pages.isEmpty()) {
                    mostrarError("Este capitulo no tiene paginas todavia.")
                    return@launch
                }

                paginas = capitulo.pages

                binding.rvTira.layoutManager = LinearLayoutManager(this@LectorActivity)
                binding.rvTira.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val pos = (binding.rvTira.layoutManager as? LinearLayoutManager)
                            ?.findFirstVisibleItemPosition() ?: return
                        if (pos >= 0) {
                            actualizarContador(pos)
                            programarGuardarProgreso(pos)
                        }
                    }
                })

                aplicarDireccion()
                aplicarModoALaVista()

                val posicionValida = paginaInicial.coerceIn(0, paginas.size - 1)
                irAPagina(posicionValida)
                binding.tvContador.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e("LectorActivity", "Error cargando paginas", e)
                binding.progressBar.visibility = View.GONE
                mostrarError(com.datgarscan.app.webapi.ErroresRed.mensajeAmable(e))
            }
        }
    }

    /** Guarda la pagina exacta en el historial, con un pequeno retraso para no saturar de peticiones. */
    private fun programarGuardarProgreso(posicionReal: Int) {
        if (!SesionManager.estaLogueado() || mangaId <= 0) return

        runnableGuardado?.let { handlerGuardado.removeCallbacks(it) }
        val nuevo = Runnable {
            lifecycleScope.launch {
                try {
                    WebApiClient.get().guardarProgreso(
                        HistorialGuardarRequest(mangaId, chapterId, posicionReal + 1)
                    )
                } catch (e: Exception) {
                    Log.w("LectorActivity", "No se pudo guardar el progreso: ${e.message}")
                }
            }
        }
        runnableGuardado = nuevo
        handlerGuardado.postDelayed(nuevo, 800)
    }

    private fun mostrarError(mensaje: String) {
        binding.tvError.text = mensaje
        binding.tvError.visibility = View.VISIBLE
    }

    /**
     * Reclama la garrita sorpresa del capitulo actual. Se deshabilita el
     * icono de inmediato para evitar toques repetidos mientras responde el
     * servidor.
     */
    private fun reclamarSorpresa(chapterId: Int) {
        binding.tvSorpresa.setOnClickListener(null)
        binding.tvSorpresa.alpha = 0.5f

        lifecycleScope.launch {
            try {
                val estado = WebApiClient.get().reclamarSorpresa(chapterId = chapterId)
                if (estado.success) {
                    Toast.makeText(
                        this@LectorActivity,
                        estado.message ?: "+${estado.ganadas ?: 0} garritas",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.tvSorpresa.visibility = View.GONE
                } else {
                    Toast.makeText(this@LectorActivity, estado.message ?: "No se pudo reclamar.", Toast.LENGTH_SHORT).show()
                    binding.tvSorpresa.visibility = View.GONE
                }
            } catch (e: Exception) {
                Toast.makeText(this@LectorActivity, "No se pudo reclamar la sorpresa.", Toast.LENGTH_SHORT).show()
                binding.tvSorpresa.alpha = 1f
                binding.tvSorpresa.setOnClickListener { reclamarSorpresa(chapterId) }
            }
        }
    }

    /**
     * Muestra el anuncio de salida (si toca, ver AnunciosManager) y luego
     * cierra el lector. Se llama tanto desde el boton propio de "Volver" como
     * desde el boton atras del sistema, para cubrir ambas formas de salir.
     */
    private fun salirDelLector() {
        com.datgarscan.app.ads.AnunciosManager.registrarSalidaDeLector(this)
        finish()
    }

    @Suppress("MissingSuperCall", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        salirDelLector()
    }
}
