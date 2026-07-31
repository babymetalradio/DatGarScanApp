package com.datgarscan.app.historial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.datgarscan.app.R
import com.datgarscan.app.webapi.HistorialItem

class HistorialAdapter(
    private var items: List<HistorialItem> = emptyList(),
    private val onClick: (HistorialItem) -> Unit
) : RecyclerView.Adapter<HistorialAdapter.HistorialViewHolder>() {

    inner class HistorialViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPortada: ImageView = view.findViewById(R.id.ivPortada)
        val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        val tvCapitulo: TextView = view.findViewById(R.id.tvCapitulo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistorialViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_historial, parent, false)
        return HistorialViewHolder(vista)
    }

    override fun onBindViewHolder(holder: HistorialViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitulo.text = item.title
        val numero = if (item.chapter_number == item.chapter_number.toLong().toDouble())
            item.chapter_number.toLong().toString() else item.chapter_number.toString()
        holder.tvCapitulo.text = "Cap $numero"

        Glide.with(holder.itemView.context).load(item.cover_url).centerCrop().into(holder.ivPortada)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun actualizar(nuevaLista: List<HistorialItem>) {
        items = nuevaLista
        notifyDataSetChanged()
    }
}
