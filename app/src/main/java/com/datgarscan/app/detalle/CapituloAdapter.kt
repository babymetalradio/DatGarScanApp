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

class CapituloAdapter(
    private var capitulos: List<CapituloResumen> = emptyList(),
    private val onClick: (CapituloResumen) -> Unit
) : RecyclerView.Adapter<CapituloAdapter.CapituloViewHolder>() {

    inner class CapituloViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CapituloViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_capitulo, parent, false)
        return CapituloViewHolder(vista)
    }

    override fun onBindViewHolder(holder: CapituloViewHolder, position: Int) {
        val cap = capitulos[position]
        val numero = if (cap.chapter_number == cap.chapter_number.toLong().toDouble())
            cap.chapter_number.toLong().toString() else cap.chapter_number.toString()
        holder.tvTitulo.text = "Cap $numero" + if (!cap.title.isNullOrBlank()) " — ${cap.title}" else ""
        holder.tvFecha.text = "${cap.pages} págs · ${formatearFecha(cap.created_at)}"
        holder.itemView.setOnClickListener { onClick(cap) }
    }

    override fun getItemCount(): Int = capitulos.size

    fun actualizar(nuevaLista: List<CapituloResumen>) {
        capitulos = nuevaLista
        notifyDataSetChanged()
    }

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
