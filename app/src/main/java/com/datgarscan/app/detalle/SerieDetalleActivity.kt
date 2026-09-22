package com.datgarscan.app.detalle

import com.datgarscan.app.BaseActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.datgarscan.app.R
import com.datgarscan.app.databinding.ActivitySerieDetalleBinding
import com.datgarscan.app.descargas.DescargasManager
import com.datgarscan.app.lector.LectorActivity
import com.datgarscan.app.tienda.ProManager
import com.datgarscan.app.tienda.TiendaActivity
import com.datgarscan.app.webapi.CapituloResumen
import com.datgarscan.app.webapi.WebApiClient
import kotlinx.coroutines.launch

class SerieDetalleActivity : BaseActivity() {

    companion object {
        private const val EXTRA_SLUG = "extra_slug"

        fun crearIntent(context: Context, slug: String): Intent {
            return Intent(context, SerieDetalleActivity::class.java)
                .putExtra(EXTRA_SLUG, slug)
        }
    }

    private lateinit var binding: ActivitySerieDetalleBinding
    private lateinit var adapter: CapituloAdapter
    private var mangaIdActual: Int? = null
    private var esFavoritoActual: Boolean = false
    private var slugActual: String = ""
    private var mangaTitleActual: String = ""
    private var coverUrlActual: String? = null
    private var capitulosActuales: List<CapituloResumen> = emptyList()
    private var descripcionActual: String = ""
    private var generosActuales: String = ""
    private var autorActual: String = ""
    private var estadoActual: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySerieDetalleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val slug = intent.getStringExtra(EXTRA_SLUG)
        if (slug == null) {
            Toast.makeText(this, "Serie inválida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        slugActual = slug

        binding.tvVolver.setOnClickListener { finish() }
        binding.tvFavorito.setOnClickListener { alternarFavorito() }
        binding.tvReportar.setOnClickListener {
            mostrarDialogoReporte(mangaTitleActual)
        }

        adapter = CapituloAdapter(
            onClick = { capitulo -> startActivity(LectorActivity.crearIntent(this, capitulo.id)) },
            onDescargar = { capitulo -> descargarCapitulo(capitulo) },
            onBorrarDescarga = { capitulo -> borrarDescarga(capitulo) }
        )
        binding.rvCapitulos.layoutManager = LinearLayoutManager(this)
        binding.rvCapitulos.adapter = adapter

        binding.tvLeerPrimero.setOnClickListener {
            capitulosActuales.minByOrNull { it.chapter_number }?.let {
                startActivity(LectorActivity.crearIntent(this, it.id))
            }
        }
        binding.tvLeerUltimo.setOnClickListener {
            capitulosActuales.maxByOrNull { it.chapter_number }?.let {
                startActivity(LectorActivity.crearIntent(this, it.id))
            }
        }

        com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerAds)

        cargarDetalle(slug)
    }

    private fun alternarFavorito() {
        val mangaId = mangaIdActual ?: return

        if (!com.datgarscan.app.webapi.SesionManager.estaLogueado()) {
            Toast.makeText(this, "Inicia sesión para guardar favoritos.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().alternarFavorito(
                    com.datgarscan.app.webapi.FavoritoToggleRequest(mangaId)
                )
                if (respuesta.success) {
                    esFavoritoActual = respuesta.es_favorito
                    actualizarBotonFavorito()
                } else {
                    Toast.makeText(this@SerieDetalleActivity, respuesta.message ?: "No se pudo actualizar.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SerieDetalleActivity, "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun actualizarBotonFavorito() {
        binding.tvFavorito.text = if (esFavoritoActual) "En favoritos" else "Favorito"
        binding.tvFavorito.setTextColor(
            resources.getColor(if (esFavoritoActual) R.color.accent else R.color.muted, theme)
        )
    }

    private fun cargarDetalle(slug: String) {
        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().obtenerDetalle(slug)

                if (!respuesta.success || respuesta.data == null) {
                    Toast.makeText(this@SerieDetalleActivity, respuesta.message ?: "No se encontró la serie.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                val manga = respuesta.data
                mangaIdActual = manga.id
                esFavoritoActual = manga.es_favorito
                actualizarBotonFavorito()

                binding.tvNombreSerie.text = manga.title
                binding.tvAutorDetalle.text = manga.author?.takeIf { it.isNotBlank() } ?: "Autor desconocido"
                binding.tvContador.text = "${manga.chapters.size} capítulos"

                if (manga.genres.isNotEmpty()) {
                    binding.tvGenerosDetalle.visibility = View.VISIBLE
                    binding.tvGenerosDetalle.text = manga.genres.joinToString(" · ")
                }

                val descripcion = manga.description?.takeIf { it.isNotBlank() }
                descripcionActual = descripcion ?: ""
                generosActuales = manga.genres.joinToString(" · ")
                autorActual = manga.author?.takeIf { it.isNotBlank() } ?: "Autor desconocido"
                estadoActual = traducirEstado(manga.status)

                if (descripcion != null) {
                    binding.tvDescripcionDetalle.visibility = View.VISIBLE
                    binding.tvDescripcionDetalle.text = descripcion
                    binding.tvDescripcionDetalle.maxLines = 4
                    binding.tvDescripcionDetalle.setOnClickListener {
                        mostrarPopupInfoManga()
                    }
                }


                Glide.with(this@SerieDetalleActivity).load(manga.cover_url).into(binding.ivPortadaDetalle)

                mangaTitleActual = manga.title
                coverUrlActual = manga.cover_url
                capitulosActuales = manga.chapters
                adapter.actualizar(manga.chapters)

                val idsDescargados = manga.chapters
                    .filter { DescargasManager.estaDescargado(this@SerieDetalleActivity, it.id) }
                    .map { it.id }
                    .toSet()
                if (idsDescargados.isNotEmpty()) {
                    adapter.marcarDescargados(idsDescargados)
                }

            } catch (e: Exception) {
                Log.e("SerieDetalleActivity", "Error cargando detalle", e)
                Toast.makeText(this@SerieDetalleActivity, com.datgarscan.app.webapi.ErroresRed.mensajeAmable(e), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun descargarCapitulo(capitulo: CapituloResumen) {
        if (!ProManager.esPro(this)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Función Pro")
                .setMessage("Descargar capítulos para leer sin conexión es un beneficio Pro.\n\nCanjea tus garritas por Pro en la Tienda.")
                .setPositiveButton("Ir a la Tienda") { _, _ -> startActivity(TiendaActivity.crearIntent(this)) }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }

        val mangaId = mangaIdActual ?: return
        adapter.actualizarEstadoDescarga(capitulo.id, EstadoDescargaCap.Descargando(0, capitulo.pages))

        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().obtenerCapitulo(capitulo.id)
                val paginasCapitulo = respuesta.data?.pages ?: emptyList()

                if (!respuesta.success || paginasCapitulo.isEmpty()) {
                    Toast.makeText(this@SerieDetalleActivity, "No se pudo descargar el capítulo.", Toast.LENGTH_SHORT).show()
                    adapter.actualizarEstadoDescarga(capitulo.id, EstadoDescargaCap.NoDescargado)
                    return@launch
                }

                val exito = DescargasManager.descargarCapitulo(
                    context = this@SerieDetalleActivity,
                    chapterId = capitulo.id,
                    mangaId = mangaId,
                    mangaSlug = slugActual,
                    mangaTitle = mangaTitleActual,
                    chapterNumber = capitulo.chapter_number,
                    chapterTitle = capitulo.title,
                    coverUrl = coverUrlActual,
                    paginasUrls = paginasCapitulo,
                    onProgreso = { descargadas, total ->
                        runOnUiThread {
                            adapter.actualizarEstadoDescarga(capitulo.id, EstadoDescargaCap.Descargando(descargadas, total))
                        }
                    }
                )

                adapter.actualizarEstadoDescarga(
                    capitulo.id,
                    if (exito) EstadoDescargaCap.Descargado else EstadoDescargaCap.NoDescargado
                )
                if (!exito) {
                    Toast.makeText(this@SerieDetalleActivity, "Falló la descarga, intenta de nuevo.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SerieDetalleActivity, "Capítulo descargado.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SerieDetalleActivity", "Error descargando capitulo", e)
                adapter.actualizarEstadoDescarga(capitulo.id, EstadoDescargaCap.NoDescargado)
                Toast.makeText(this@SerieDetalleActivity, "Error de conexión al descargar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun borrarDescarga(capitulo: CapituloResumen) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Borrar descarga")
            .setMessage("¿Borrar la descarga del Cap ${capitulo.chapter_number}?")
            .setPositiveButton("Borrar") { _, _ ->
                DescargasManager.borrarDescarga(this, capitulo.id)
                adapter.actualizarEstadoDescarga(capitulo.id, EstadoDescargaCap.NoDescargado)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    private fun mostrarDialogoReporte(mangaPrellenado: String = "") {
        val contenedor = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val etAsunto = android.widget.EditText(this).apply {
            hint = "Asunto (ej: Error al cargar, contenido incorrecto...)"
            setPadding(30, 20, 30, 20)
        }
        val etManga = android.widget.EditText(this).apply {
            hint = "Manga (opcional)"
            setText(mangaPrellenado)
            setPadding(30, 20, 30, 20)
        }
        val etReporte = android.widget.EditText(this).apply {
            hint = "Describe el problema..."
            minLines = 4
            gravity = android.view.Gravity.TOP
            setPadding(30, 20, 30, 20)
        }

        contenedor.addView(etAsunto)
        contenedor.addView(etManga)
        contenedor.addView(etReporte)

        android.app.AlertDialog.Builder(this)
            .setTitle("Reportar un problema")
            .setView(contenedor)
            .setPositiveButton("Enviar") { _, _ ->
                val asunto = etAsunto.text.toString().trim()
                val manga = etManga.text.toString().trim()
                val reporte = etReporte.text.toString().trim()

                if (asunto.isBlank() || reporte.isBlank()) {
                    Toast.makeText(this, "Asunto y reporte son obligatorios.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                enviarReporte(asunto, manga, reporte)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarReporte(asunto: String, manga: String, reporte: String) {
        lifecycleScope.launch {
            try {
                val respuesta = WebApiClient.get().enviarReporte(
                    com.datgarscan.app.webapi.ReporteRequest(asunto, manga, reporte)
                )
                Toast.makeText(
                    this@SerieDetalleActivity,
                    respuesta.message ?: if (respuesta.success) "Reporte enviado." else "No se pudo enviar.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SerieDetalleActivity,
                    com.datgarscan.app.webapi.ErroresRed.mensajeAmable(e),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun traducirEstado(status: String?): String {
        return when (status) {
            "ongoing" -> "En curso"
            "completed" -> "Completado"
            "hiatus" -> "En pausa"
            "cancelled" -> "Cancelado"
            else -> status?.takeIf { it.isNotBlank() } ?: "Desconocido"
        }
    }

    private fun mostrarPopupInfoManga() {
        val densidad = resources.displayMetrics.density
        fun dp(v: Int) = (v * densidad).toInt()

        val contenedor = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }

        val filaTitulo = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val tvTitulo = android.widget.TextView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = mangaTitleActual.ifBlank { "Información" }
            setTextColor(resources.getColor(R.color.white, theme))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, dp(8), 0)
        }

        val btnCerrar = android.widget.TextView(this).apply {
            text = "✕"
            setTextColor(resources.getColor(R.color.muted, theme))
            textSize = 20f
            setPadding(dp(12), dp(4), dp(4), dp(4))
        }

        filaTitulo.addView(tvTitulo)
        filaTitulo.addView(btnCerrar)

        val meta = buildString {
            append("Autor: ").append(autorActual).append('\n')
            append("Estado: ").append(estadoActual)
            if (generosActuales.isNotBlank()) {
                append('\n').append("Géneros: ").append(generosActuales)
            }
            append('\n').append("Capítulos: ").append(capitulosActuales.size)
        }

        val tvMeta = android.widget.TextView(this).apply {
            text = meta
            setTextColor(resources.getColor(R.color.accent, theme))
            textSize = 13f
            setPadding(0, dp(12), 0, dp(8))
        }

        val tvSinopsisLabel = android.widget.TextView(this).apply {
            text = "Sinopsis"
            setTextColor(resources.getColor(R.color.white, theme))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(6))
        }

        val tvSinopsis = android.widget.TextView(this).apply {
            text = descripcionActual.ifBlank { "Sin descripción todavía." }
            setTextColor(resources.getColor(R.color.muted, theme))
            textSize = 13f
            setLineSpacing(0f, 1.15f)
        }

        val scroll = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.55f).toInt()
            )
            isFillViewport = true
            addView(tvSinopsis)
        }

        contenedor.addView(filaTitulo)
        contenedor.addView(tvMeta)
        contenedor.addView(tvSinopsisLabel)
        contenedor.addView(scroll)

        val dialogo = android.app.AlertDialog.Builder(this)
            .setView(contenedor)
            .create()

        btnCerrar.setOnClickListener { dialogo.dismiss() }
        dialogo.setCanceledOnTouchOutside(true)
        dialogo.show()

        dialogo.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(resources.getColor(R.color.surface, theme))
        )
        dialogo.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }


}
