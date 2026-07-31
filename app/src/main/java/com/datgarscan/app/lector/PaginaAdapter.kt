package com.datgarscan.app.lector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.datgarscan.app.R

class PaginaAdapter(
    private val urls: List<String>,
    private val onTap: (() -> Unit)? = null,
    private val onZoomChanged: ((Boolean) -> Unit)? = null
) : RecyclerView.Adapter<PaginaAdapter.PaginaViewHolder>() {

    inner class PaginaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iv: ZoomableImageView = itemView.findViewById(R.id.ivPagina)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaginaViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pagina, parent, false)
        return PaginaViewHolder(vista)
    }

    override fun onBindViewHolder(holder: PaginaViewHolder, position: Int) {
        holder.iv.onTap = onTap
        holder.iv.onZoomChanged = onZoomChanged
        Glide.with(holder.itemView.context)
            .load(urls[position])
            .into(holder.iv)
    }

    override fun getItemCount(): Int = urls.size
}
