package com.datgarscan.app.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.datgarscan.app.R
import com.datgarscan.app.webapi.CapituloPendiente
import com.datgarscan.app.webapi.MangaPendiente

private const val TIPO_HEADER = 0
private const val TIPO_CAPITULO = 1

private sealed class Fila {
    data class Header(val manga: MangaPendiente) : Fila()
    data class Item(val manga: MangaPendiente, val capitulo: CapituloPendiente) : Fila()
}

sealed class EstadoImportacion {
    object Inactivo : EstadoImportacion()
    object Importando : EstadoImportacion()
    object Importado : EstadoImportacion()
}

class PendientesAdapter(
    private val onImportar: (MangaPendiente, CapituloPendiente) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var filas: List<Fila> = emptyList()
    // clave: "mangaId-numero"
    private val estados = mutableMapOf<String, EstadoImportacion>()

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val titulo: TextView = view.findViewById(R.id.tvMangaTitulo)
        val subtitulo: TextView = view.findViewById(R.id.tvMangaSubtitulo)
    }

    inner class CapVH(view: View) : RecyclerView.ViewHolder(view) {
        val titulo: TextView = view.findViewById(R.id.tvCapTitulo)
        val paginas: TextView = view.findViewById(R.id.tvCapPaginas)
        val importar: TextView = view.findViewById(R.id.tvImportar)
    }

    override fun getItemViewType(position: Int) =
        if (filas[position] is Fila.Header) TIPO_HEADER else TIPO_CAPITULO

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_admin_manga_header, parent, false))
        } else {
            CapVH(inflater.inflate(R.layout.item_admin_capitulo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val fila = filas[position]) {
            is Fila.Header -> {
                holder as HeaderVH
                holder.titulo.text = fila.manga.title
                holder.subtitulo.text = if (fila.manga.error != null) {
                    "Error: ${fila.manga.error}"
                } else {
                    val n = fila.manga.capitulos.size
                    if (n == 1) "1 capítulo pendiente" else "$n capítulos pendientes"
                }
            }
            is Fila.Item -> {
                holder as CapVH
                val numero = formatearNumero(fila.capitulo.numero)
                holder.titulo.text = "Cap $numero"
                holder.paginas.text = "${fila.capitulo.paginas} páginas en el blog"

                val clave = "${fila.manga.manga_id}-$numero"
                when (estados[clave] ?: EstadoImportacion.Inactivo) {
                    is EstadoImportacion.Importado -> {
                        holder.importar.text = "✓ Listo"
                        holder.importar.setOnClickListener(null)
                    }
                    is EstadoImportacion.Importando -> {
                        holder.importar.text = "..."
                        holder.importar.setOnClickListener(null)
                    }
                    is EstadoImportacion.Inactivo -> {
                        holder.importar.text = "Importar"
                        holder.importar.setOnClickListener { onImportar(fila.manga, fila.capitulo) }
                    }
                }
            }
        }
    }

    override fun getItemCount() = filas.size

    fun actualizar(lista: List<MangaPendiente>) {
        val nuevasFilas = mutableListOf<Fila>()
        lista.forEach { manga ->
            nuevasFilas.add(Fila.Header(manga))
            manga.capitulos.forEach { cap -> nuevasFilas.add(Fila.Item(manga, cap)) }
        }
        filas = nuevasFilas
        estados.clear()
        notifyDataSetChanged()
    }

    fun actualizarEstado(mangaId: Int, numero: Double, estado: EstadoImportacion) {
        val clave = "$mangaId-${formatearNumero(numero)}"
        estados[clave] = estado
        val idx = filas.indexOfFirst {
            it is Fila.Item && it.manga.manga_id == mangaId && formatearNumero(it.capitulo.numero) == formatearNumero(numero)
        }
        if (idx >= 0) notifyItemChanged(idx)
    }

    private fun formatearNumero(numero: Double): String =
        if (numero == numero.toLong().toDouble()) numero.toLong().toString() else numero.toString()
}
