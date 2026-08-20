package com.datgarscan.app.detalle

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.datgarscan.app.R
import com.datgarscan.app.webapi.CapituloResumen
import java.text.SimpleDateFormat
import java.util.Locale

sealed class EstadoDescargaCap {
    object NoDescargado : EstadoDescargaCap()
    data class Descargando(val progreso: Int, val total: Int) : EstadoDescargaCap()
    object Descargado : EstadoDescargaCap()
}

private const val TAMANO_GRUPO = 30
private const val TIPO_HEADER = 0
private const val TIPO_CAPITULO = 1

private sealed class Fila {
    data class Header(val grupoIndex: Int, val titulo: String) : Fila()
    data class Item(val capitulo: CapituloResumen) : Fila()
}

class CapituloAdapter(
    private var capitulosOriginal: List<CapituloResumen> = emptyList(),
    private val onClick: (CapituloResumen) -> Unit,
    private val onDescargar: (CapituloResumen) -> Unit,
    private val onBorrarDescarga: (CapituloResumen) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val gruposExpandidos = mutableSetOf(0) // el primer grupo (mas reciente) abierto por defecto
    private val estadosDescarga = mutableMapOf<Int, EstadoDescargaCap>()
    private var filas: List<Fila> = emptyList()

    init {
        reconstruirFilas()
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloGrupo)
        val tvFlecha: TextView = view.findViewById(R.id.tvFlechaGrupo)
    }

    inner class CapituloViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val tvDescargar: TextView = view.findViewById(R.id.tvDescargar)
    }

    override fun getItemViewType(position: Int): Int =
        when (filas[position]) {
            is Fila.Header -> TIPO_HEADER
            is Fila.Item -> TIPO_CAPITULO
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_grupo_capitulos, parent, false))
        } else {
            CapituloViewHolder(inflater.inflate(R.layout.item_capitulo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val fila = filas[position]) {
            is Fila.Header -> {
                holder as HeaderViewHolder
                holder.tvTitulo.text = fila.titulo
                val expandido = fila.grupoIndex in gruposExpandidos
                holder.tvFlecha.text = if (expandido) "▾" else "▸"
                holder.itemView.setOnClickListener {
                    if (expandido) gruposExpandidos.remove(fila.grupoIndex) else gruposExpandidos.add(fila.grupoIndex)
                    reconstruirFilas()
                    notifyDataSetChanged()
                }
            }
            is Fila.Item -> {
                holder as CapituloViewHolder
                val cap = fila.capitulo
                val numero = formatearNumero(cap.chapter_number)
                holder.tvTitulo.text = "Cap $numero" + if (!cap.title.isNullOrBlank()) " — ${cap.title}" else ""
                holder.tvFecha.text = "${cap.pages} págs · ${formatearFecha(cap.created_at)}"
                holder.itemView.setOnClickListener { onClick(cap) }

                when (val estado = estadosDescarga[cap.id] ?: EstadoDescargaCap.NoDescargado) {
                    is EstadoDescargaCap.Descargado -> {
                        holder.tvDescargar.text = "✓"
                        holder.tvDescargar.setOnClickListener { onBorrarDescarga(cap) }
                    }
                    is EstadoDescargaCap.Descargando -> {
                        holder.tvDescargar.text = if (estado.total > 0)
                            "${(estado.progreso * 100 / estado.total)}%" else "…"
                        holder.tvDescargar.setOnClickListener(null)
                    }
                    is EstadoDescargaCap.NoDescargado -> {
                        holder.tvDescargar.text = "⬇"
                        holder.tvDescargar.setOnClickListener { onDescargar(cap) }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = filas.size

    fun actualizar(nuevaLista: List<CapituloResumen>) {
        capitulosOriginal = nuevaLista
        gruposExpandidos.clear()
        gruposExpandidos.add(0)
        reconstruirFilas()
        notifyDataSetChanged()
    }

    /** Llamar desde la Activity para reflejar progreso/estado de una descarga sin recargar todo. */
    fun actualizarEstadoDescarga(chapterId: Int, estado: EstadoDescargaCap) {
        estadosDescarga[chapterId] = estado
        val idx = filas.indexOfFirst { it is Fila.Item && it.capitulo.id == chapterId }
        if (idx >= 0) notifyItemChanged(idx)
    }

    fun marcarDescargados(idsDescargados: Set<Int>) {
        idsDescargados.forEach { estadosDescarga[it] = EstadoDescargaCap.Descargado }
        notifyDataSetChanged()
    }

    private fun reconstruirFilas() {
        val ordenados = capitulosOriginal.sortedByDescending { it.chapter_number }
        val nuevasFilas = mutableListOf<Fila>()
        ordenados.chunked(TAMANO_GRUPO).forEachIndexed { grupoIndex, grupo ->
            val primero = formatearNumero(grupo.first().chapter_number)
            val ultimo = formatearNumero(grupo.last().chapter_number)
            nuevasFilas.add(Fila.Header(grupoIndex, "Cap $ultimo — $primero"))
            if (grupoIndex in gruposExpandidos) {
                grupo.forEach { nuevasFilas.add(Fila.Item(it)) }
            }
        }
        filas = nuevasFilas
    }

    private fun formatearNumero(numero: Double): String =
        if (numero == numero.toLong().toDouble()) numero.toLong().toString() else numero.toString()

    private fun formatearFecha(fecha: String?): String {
        if (fecha.isNullOrBlank()) return ""
        return try {
            val entrada = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val salida = SimpleDateFormat("d MMM yyyy", Locale("es"))
            salida.format(entrada.parse(fecha) ?: return fecha)
        } catch (e: Exception) {
            fecha
        }
    }
}
