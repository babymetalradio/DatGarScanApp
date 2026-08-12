package com.datgarscan.app.webapi

data class MangaResumen(
    val id: Int,
    val slug: String,
    val title: String,
    val cover_url: String?,
    val author: String?,
    val status: String?,
    val genres: List<String> = emptyList(),
    val views: Int = 0,
    val downloads: Int = 0,
    val chapter_count: Int = 0,
    val last_chapter_at: String? = null
)

data class MangasListResponse(
    val success: Boolean,
    val count: Int = 0,
    val data: List<MangaResumen> = emptyList(),
    val message: String? = null
)

data class CapituloResumen(
    val id: Int,
    val chapter_number: Double,
    val title: String?,
    val pages: Int = 0,
    val file_size: Long = 0,
    val views: Int = 0,
    val created_at: String? = null
)

data class MangaDetalle(
    val id: Int,
    val slug: String,
    val title: String,
    val author: String?,
    val status: String?,
    val description: String?,
    val genres: List<String> = emptyList(),
    val views: Int = 0,
    val downloads: Int = 0,
    val es_favorito: Boolean = false,
    val cover_url: String?,
    val chapters: List<CapituloResumen> = emptyList()
)

data class MangaDetalleResponse(
    val success: Boolean,
    val data: MangaDetalle? = null,
    val message: String? = null
)

data class CapituloPaginas(
    val id: Int,
    val manga_id: Int,
    val manga_slug: String,
    val manga_title: String,
    val chapter_number: Double,
    val title: String?,
    val pages: List<String> = emptyList(),
    val prev_chapter_id: Int? = null,
    val next_chapter_id: Int? = null,
    val tiene_sorpresa: Boolean = false
)

data class CapituloResponse(
    val success: Boolean,
    val data: CapituloPaginas? = null,
    val message: String? = null
)

data class UserApi(
    val id: Int,
    val username: String,
    val email: String,
    val role: String
)

data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
    val user: UserApi? = null,
    val message: String? = null
)

data class LoginRequest(val username: String, val password: String)
data class RegistroRequest(val username: String, val email: String, val password: String)

data class FavoritoManga(
    val id: Int,
    val slug: String,
    val title: String,
    val author: String?,
    val cover_url: String?,
    val chapter_count: Int = 0
)

data class FavoritosResponse(
    val success: Boolean,
    val data: List<FavoritoManga> = emptyList(),
    val message: String? = null
)

data class FavoritoToggleRequest(val manga_id: Int)
data class FavoritoToggleResponse(
    val success: Boolean,
    val es_favorito: Boolean = false,
    val message: String? = null
)

data class HistorialItem(
    val manga_id: Int,
    val slug: String,
    val title: String,
    val cover_url: String?,
    val chapter_id: Int,
    val chapter_number: Double,
    val chapter_title: String?,
    val page_number: Int,
    val updated_at: String?
)

data class HistorialResponse(
    val success: Boolean,
    val data: List<HistorialItem> = emptyList(),
    val message: String? = null
)

data class HistorialGuardarRequest(val manga_id: Int, val chapter_id: Int, val page_number: Int)
data class RespuestaSimple(val success: Boolean, val message: String? = null)
data class TokenRequest(val token: String)

data class PopupData(
    val version: Int,
    val titulo: String,
    val mensaje: String,
    val texto_boton: String? = null,
    val url_boton: String? = null
)

data class PopupResponse(
    val success: Boolean,
    val mostrar: Boolean = false,
    val data: PopupData? = null
)

data class GarritasEstado(
    val success: Boolean,
    val saldo: Int = 0,
    val sin_anuncios: Boolean = false,
    val sin_anuncios_hasta: String? = null,
    val bonus_diario_disponible: Boolean = false,
    val anuncios_vistos_hoy: Int = 0,
    val max_anuncios_dia: Int = 10,
    val garritas_por_anuncio: Int = 10,
    val garritas_bonus_diario: Int = 5,
    val ganadas: Int? = null,
    val message: String? = null
)

data class CodigoRequest(val codigo: String)

data class PerfilData(
    val username: String,
    val email: String? = null,
    val miembro_desde: String? = null,
    val favoritos: Int = 0,
    val capitulos_leidos: Int = 0,
    val series_leidas: Int = 0,
    val garritas: Int = 0,
    val sin_anuncios: Boolean = false
)

data class PerfilResponse(
    val success: Boolean,
    val data: PerfilData? = null,
    val message: String? = null
)

data class VersionResponse(
    val success: Boolean,
    val hay_version: Boolean = false,
    val version_name: String? = null,
    val apk_url: String? = null,
    val notas: String? = null,
    val obligatorio: Boolean = false
)
