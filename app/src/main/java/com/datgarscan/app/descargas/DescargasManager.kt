package com.datgarscan.app.descargas

import android.content.Context
import com.datgarscan.app.webapi.WebApiClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Maneja las descargas de capitulos para lectura sin conexion. Las paginas
 * se guardan cifradas (AES/GCM, ver CifradorDescargas) dentro del almacenamiento
 * privado de la app (filesDir), donde ninguna otra app ni el explorador de
 * archivos del sistema puede leerlas directamente. Para lectura, se
 * descifran a un archivo temporal en el cache privado de la app y se borran
 * en cuanto se cierra el capitulo (ver LectorActivity).
 */
object DescargasManager {

    private const val ARCHIVO_METADATOS = "descargas_metadata.json"
    private const val CARPETA_DESCARGAS = "descargas"
    private const val CARPETA_LECTURA_TEMPORAL = "lectura_offline_tmp"
    private val gson = Gson()

    private fun archivoMetadatos(context: Context) = File(context.filesDir, ARCHIVO_METADATOS)

    private fun carpetaCapitulo(context: Context, chapterId: Int) =
        File(context.filesDir, "$CARPETA_DESCARGAS/$chapterId")

    // ---------- Metadatos ----------

    @Synchronized
    private fun leerMetadatos(context: Context): MutableList<CapituloDescargado> {
        val f = archivoMetadatos(context)
        if (!f.exists()) return mutableListOf()
        return try {
            val tipo = object : TypeToken<MutableList<CapituloDescargado>>() {}.type
            gson.fromJson(f.readText(), tipo) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    private fun guardarMetadatos(context: Context, lista: List<CapituloDescargado>) {
        try {
            archivoMetadatos(context).writeText(gson.toJson(lista))
        } catch (e: Exception) { /* si falla, la proxima lectura lo detecta como vacio */ }
    }

    fun listarDescargas(context: Context): List<CapituloDescargado> =
        leerMetadatos(context).sortedByDescending { it.descargadoEn }

    fun estaDescargado(context: Context, chapterId: Int): Boolean =
        leerMetadatos(context).any { it.chapterId == chapterId }

    fun obtenerMetadato(context: Context, chapterId: Int): CapituloDescargado? =
        leerMetadatos(context).find { it.chapterId == chapterId }

    // ---------- Descargar ----------

    /**
     * Descarga y cifra todas las paginas de un capitulo. Devuelve true si
     * termino bien. Corre en Dispatchers.IO, se puede llamar desde
     * lifecycleScope.launch directo.
     */
    suspend fun descargarCapitulo(
        context: Context,
        chapterId: Int,
        mangaId: Int,
        mangaSlug: String,
        mangaTitle: String,
        chapterNumber: Double,
        chapterTitle: String?,
        coverUrl: String?,
        paginasUrls: List<String>,
        onProgreso: (descargadas: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (paginasUrls.isEmpty()) return@withContext false

        val carpeta = carpetaCapitulo(context, chapterId)
        try {
            if (carpeta.exists()) carpeta.deleteRecursively()
            carpeta.mkdirs()

            val cliente = WebApiClient.okHttp()

            paginasUrls.forEachIndexed { index, url ->
                val request = Request.Builder().url(url).build()
                cliente.newCall(request).execute().use { respuesta ->
                    if (!respuesta.isSuccessful) throw Exception("No se pudo descargar la pagina ${index + 1}")
                    val bytes = respuesta.body?.bytes() ?: throw Exception("Pagina ${index + 1} vacia")
                    val cifrado = CifradorDescargas.cifrar(bytes)
                    val nombreArchivo = "pagina_%03d.enc".format(index)
                    File(carpeta, nombreArchivo).writeBytes(cifrado)
                }
                onProgreso(index + 1, paginasUrls.size)
            }

            val metadatos = leerMetadatos(context)
            metadatos.removeAll { it.chapterId == chapterId }
            metadatos.add(
                CapituloDescargado(
                    chapterId = chapterId,
                    mangaId = mangaId,
                    mangaSlug = mangaSlug,
                    mangaTitle = mangaTitle,
                    chapterNumber = chapterNumber,
                    chapterTitle = chapterTitle,
                    totalPaginas = paginasUrls.size,
                    coverUrl = coverUrl
                )
            )
            guardarMetadatos(context, metadatos)
            true
        } catch (e: Exception) {
            // Si algo fallo a medias, no dejamos una descarga corrupta a medio cifrar.
            carpeta.deleteRecursively()
            false
        }
    }

    // ---------- Borrar ----------

    fun borrarDescarga(context: Context, chapterId: Int) {
        carpetaCapitulo(context, chapterId).deleteRecursively()
        val metadatos = leerMetadatos(context)
        metadatos.removeAll { it.chapterId == chapterId }
        guardarMetadatos(context, metadatos)
    }

    // ---------- Preparar para lectura ----------

    /**
     * Descifra las paginas del capitulo descargado a un archivo temporal en
     * el cache privado de la app, y devuelve las rutas (file://) listas
     * para pasarle al lector. Llamar a limpiarTemporalesLectura() cuando se
     * cierre el capitulo para no dejar imagenes sueltas sin cifrar.
     */
    suspend fun prepararParaLectura(context: Context, chapterId: Int): List<String> =
        withContext(Dispatchers.IO) {
            val carpetaOrigen = carpetaCapitulo(context, chapterId)
            val archivos = carpetaOrigen.listFiles { f -> f.name.endsWith(".enc") }
                ?.sortedBy { it.name } ?: return@withContext emptyList()

            val carpetaTemp = File(context.cacheDir, "$CARPETA_LECTURA_TEMPORAL/$chapterId")
            carpetaTemp.deleteRecursively()
            carpetaTemp.mkdirs()

            archivos.mapIndexed { index, archivoCifrado ->
                val bytesPlano = CifradorDescargas.descifrar(archivoCifrado.readBytes())
                val destino = File(carpetaTemp, "pagina_%03d.jpg".format(index))
                destino.writeBytes(bytesPlano)
                "file://${destino.absolutePath}"
            }
        }

    /** Borra las imagenes temporales sin cifrar creadas por prepararParaLectura(). */
    fun limpiarTemporalesLectura(context: Context, chapterId: Int) {
        File(context.cacheDir, "$CARPETA_LECTURA_TEMPORAL/$chapterId").deleteRecursively()
    }
}
