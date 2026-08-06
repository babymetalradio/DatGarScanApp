package com.datgarscan.app.webapi

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object CatalogoCache {

    private const val ARCHIVO = "catalogo_cache.json"
    private val gson = Gson()

    private fun archivo(context: Context) = File(context.filesDir, ARCHIVO)

    fun guardar(context: Context, mangas: List<MangaResumen>) {
        try {
            archivo(context).writeText(gson.toJson(mangas))
        } catch (e: Exception) { /* si falla guardar el cache, no es grave */ }
    }

    fun cargar(context: Context): List<MangaResumen>? {
        val f = archivo(context)
        if (!f.exists()) return null
        return try {
            val tipo = object : TypeToken<List<MangaResumen>>() {}.type
            gson.fromJson(f.readText(), tipo)
        } catch (e: Exception) {
            null
        }
    }
}
