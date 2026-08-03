package com.datgarscan.app.webapi

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface DatGarApiService {

    @GET("api/mangas.php")
    suspend fun listarMangas(@Query("q") busqueda: String? = null): MangasListResponse

    @GET("api/manga_detalle.php")
    suspend fun obtenerDetalle(@Query("slug") slug: String): MangaDetalleResponse

    @GET("api/capitulo.php")
    suspend fun obtenerCapitulo(@Query("id") id: Int): CapituloResponse

    @POST("api/login.php")
    suspend fun login(@Body req: LoginRequest): AuthResponse

    @POST("api/registro.php")
    suspend fun registro(@Body req: RegistroRequest): AuthResponse

    @GET("api/favoritos.php")
    suspend fun listarFavoritos(): FavoritosResponse

    @POST("api/favoritos.php")
    suspend fun alternarFavorito(@Body req: FavoritoToggleRequest): FavoritoToggleResponse

    @GET("api/historial.php")
    suspend fun listarHistorial(): HistorialResponse

    @POST("api/historial.php")
    suspend fun guardarProgreso(@Body req: HistorialGuardarRequest): RespuestaSimple

    @POST("api/registrar_token.php")
    suspend fun registrarTokenNotificaciones(@Body req: TokenRequest): RespuestaSimple

    @GET("api/popup.php")
    suspend fun obtenerPopup(): PopupResponse
}
