package com.datgarscan.app.descargas

data class CapituloDescargado(
    val chapterId: Int,
    val mangaId: Int,
    val mangaSlug: String,
    val mangaTitle: String,
    val chapterNumber: Double,
    val chapterTitle: String?,
    val totalPaginas: Int,
    val coverUrl: String?,
    val descargadoEn: Long = System.currentTimeMillis()
)
