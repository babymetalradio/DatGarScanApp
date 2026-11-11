package com.datgarscan.app.rachas

import android.content.Context
import android.util.Log
import com.datgarscan.app.webapi.HitoRacha
import com.datgarscan.app.webapi.RachaEstado
import com.datgarscan.app.webapi.RespuestaSimple
import com.datgarscan.app.webapi.WebApiClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Sistema de rachas de lectura.
 *
 * Un día de racha = al menos un capítulo leído ese día (guardarProgreso
 * exitoso en el servidor). La fuente de verdad son las fechas updated_at
 * del historial del servidor, no el reloj del teléfono: lo guardado en
 * SharedPreferences es solo un caché para funcionar sin conexión.
 *
 * El cambio de día se calcula en America/Mexico_City.
 *
 * El endpoint api/rachas.php ya está en vivo en el servidor: obtenerEstado()
 * lo usa como fuente principal y, si falla, calcula la racha desde el
 * historial. Los hitos se reclaman en el servidor; si aún no estuviera
 * disponible, el botón muestra "Próximamente".
 */
object RachaManager {

    private const val TAG = "RachaManager"
    private const val PREFS = "datgar_rachas"
    private const val KEY_DIAS_LEIDOS = "dias_leidos" // "yyyy-MM-dd,yyyy-MM-dd,..." (máx. 90 días)
    private const val KEY_HITOS_RECLAMADOS = "hitos_reclamados" // "3,7" por ejemplo
    private const val KEY_ULTIMA_LECTURA = "ultima_lectura"
    private const val MAX_DIAS_CACHE = 90

    val ZONA: TimeZone = TimeZone.getTimeZone("America/Mexico_City")

    /** Hitos: (día de racha, garritas, horas de PRO). Día 30 da 24 h de PRO. */
    val HITOS: List<Triple<Int, Int, Int>> = listOf(
        Triple(3, 10, 0),
        Triple(7, 25, 0),
        Triple(14, 50, 0),
        Triple(30, 0, 24)
    )

    /** true cuando el servidor respondió al endpoint de rachas alguna vez. */
    var servidorDisponible: Boolean = false
        private set

    private val formatoDia = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = ZONA
    }
    private val formatoHistorial = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = ZONA
    }

    /** Hoy en America/Mexico_City, a medianoche (00:00:00.000). */
    fun hoyMedianoche(): Calendar {
        return Calendar.getInstance(ZONA, Locale.US).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun diaAString(cal: Calendar): String = formatoDia.format(cal.time)

    fun stringADia(texto: String?): Calendar? {
        if (texto.isNullOrBlank()) return null
        return try {
            Calendar.getInstance(ZONA, Locale.US).apply {
                time = formatoDia.parse(texto.trim()) ?: return null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** "yyyy-MM-dd HH:mm:ss" del historial -> Calendar a medianoche de ese día. */
    fun historialADia(texto: String?): Calendar? {
        if (texto.isNullOrBlank()) return null
        return try {
            val cal = Calendar.getInstance(ZONA, Locale.US).apply {
                time = formatoHistorial.parse(texto.trim()) ?: return null
            }
            cal.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calcula (rachaActual, mejorRacha) a partir de los días leídos.
     * [dias] son días a medianoche en America/Mexico_City.
     * La racha actual son días consecutivos terminando hoy; si hoy aún no
     * se leyó pero ayer sí, la racha sigue viva (en riesgo). Si el último
     * día activo fue hace 2 días o más, la racha está rota (0).
     */
    fun calcularRacha(dias: Set<Calendar>): Pair<Int, Int> {
        if (dias.isEmpty()) return 0 to 0
        val diasMs = dias.map { it.timeInMillis }.toSet()
        val unDia = 24L * 60 * 60 * 1000
        val hoy = hoyMedianoche().timeInMillis

        // Mejor racha: la seguidilla consecutiva más larga de todo el historial.
        val ordenados = diasMs.sorted()
        var mejor = 1
        var corrida = 1
        for (i in 1 until ordenados.size) {
            if (ordenados[i] - ordenados[i - 1] == unDia) {
                corrida++
                if (corrida > mejor) mejor = corrida
            } else {
                corrida = 1
            }
        }

        // Racha actual: contar hacia atrás desde hoy (o desde ayer si hoy
        // aún no se leyó). Si el último día activo es anterior a ayer, rota.
        val ultimo = ordenados.max()
        if (hoy - ultimo > unDia) return 0 to mejor
        var actual = 0
        var cursor = if (diasMs.contains(hoy)) hoy else hoy - unDia
        while (diasMs.contains(cursor)) {
            actual++
            cursor -= unDia
        }
        return actual to mejor
    }

    /**
     * Días leídos de la semana actual (lunes a domingo), en orden Lun-Dom.
     * La semana empieza el lunes, como en México.
     */
    fun diasSemana(dias: Set<Calendar>): List<Boolean> {
        val diasMs = dias.map { it.timeInMillis }.toSet()
        val inicioSemana = hoyMedianoche().apply {
            // Calendar.MONDAY = 2; si hoy es domingo, retroceder 6 días.
            val diaSemana = get(Calendar.DAY_OF_WEEK)
            val retroceso = if (diaSemana == Calendar.SUNDAY) 6 else diaSemana - Calendar.MONDAY
            add(Calendar.DAY_OF_YEAR, -retroceso)
        }
        val unDia = 24L * 60 * 60 * 1000
        return (0 until 7).map { i ->
            diasMs.contains(inicioSemana.timeInMillis + i * unDia)
        }
    }

    /** Hitos con su estado según la racha actual y los ya reclamados. */
    fun hitosPara(rachaActual: Int, reclamados: Set<Int>): List<HitoRacha> {
        return HITOS.map { (dia, garritas, proHoras) ->
            HitoRacha(
                dia = dia,
                garritas = garritas,
                pro_horas = proHoras,
                reclamado = reclamados.contains(dia),
                disponible = rachaActual >= dia && !reclamados.contains(dia)
            )
        }
    }

    // ---------- Caché local (SharedPreferences "datgar_rachas") ----------

    private fun diasCacheados(context: Context): MutableSet<Calendar> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val guardado = prefs.getString(KEY_DIAS_LEIDOS, "") ?: ""
        return guardado.split(",")
            .mapNotNull { stringADia(it) }
            .toMutableSet()
    }

    private fun guardarDiasCacheados(context: Context, dias: Set<Calendar>) {
        val recientes = dias.sortedByDescending { it.timeInMillis }
            .take(MAX_DIAS_CACHE)
        val texto = recientes.joinToString(",") { diaAString(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DIAS_LEIDOS, texto)
            .apply()
    }

    fun hitosReclamados(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val guardado = prefs.getString(KEY_HITOS_RECLAMADOS, "") ?: ""
        return guardado.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    private fun marcarHitoReclamado(context: Context, dia: Int) {
        val reclamados = hitosReclamados(context).toMutableSet()
        reclamados.add(dia)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_HITOS_RECLAMADOS, reclamados.joinToString(","))
            .apply()
    }

    /**
     * Registra que hoy se leyó al menos un capítulo. Solo actualiza el
     * caché local; la fuente de verdad sigue siendo el historial del
     * servidor. Se llama tras un guardarProgreso exitoso en LectorActivity.
     */
    fun marcarLecturaHoy(context: Context) {
        try {
            val dias = diasCacheados(context)
            val hoy = hoyMedianoche()
            if (dias.any { it.timeInMillis == hoy.timeInMillis }) return
            dias.add(hoy)
            guardarDiasCacheados(context, dias)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ULTIMA_LECTURA, diaAString(hoy))
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo marcar lectura de hoy: ${e.message}")
        }
    }

    // ---------- Estado (servidor primero, historial como respaldo) ----------

    /**
     * Estado actual de la racha. Intenta api/rachas.php; si el servidor aún
     * no tiene el endpoint (o no hay red), calcula la racha desde el
     * historial del servidor y, en último caso, desde el caché local.
     * Nunca lanza excepciones.
     */
    suspend fun obtenerEstado(context: Context): RachaEstado {
        // 1) Endpoint nuevo (cuando el servidor lo tenga).
        try {
            val respuesta = WebApiClient.get().obtenerRacha()
            if (respuesta.success) {
                servidorDisponible = true
                return respuesta.copy(
                    hitos = respuesta.hitos.ifEmpty {
                        hitosPara(respuesta.racha_actual, hitosReclamados(context))
                    }
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "api/rachas.php aún no disponible: ${e.message}")
        }

        // 2) Respaldo: fechas updated_at del historial del servidor.
        val dias = try {
            val historial = WebApiClient.get().listarHistorial()
            if (historial.success) {
                historial.data.mapNotNull { historialADia(it.updated_at) }.toSet()
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cargar el historial para la racha: ${e.message}")
            emptySet()
        }

        // 3) Último recurso: caché local (lo marcado al leer sin conexión).
        val diasTotales = (dias + diasCacheados(context)).toSet()
        val (actual, mejor) = calcularRacha(diasTotales)
        val ultimo = diasTotales.maxByOrNull { it.timeInMillis }
        return RachaEstado(
            success = true,
            racha_actual = actual,
            mejor_racha = mejor,
            ultima_actividad = ultimo?.let { diaAString(it) },
            dias_semana = diasSemana(diasTotales),
            hitos = hitosPara(actual, hitosReclamados(context)),
            message = if (servidorDisponible) null else "Mostrando racha calculada desde tu historial"
        )
    }

    /**
     * Reclama la recompensa de un hito en el servidor. Si el endpoint aún
     * no existe, devuelve éxito=false con mensaje "Próximamente" y no
     * marca nada como reclamado (nada destructivo).
     */
    suspend fun reclamarHito(context: Context, dia: Int): RespuestaSimple {
        return try {
            val respuesta = WebApiClient.get().reclamarHitoRacha(hito = dia)
            if (respuesta.success) {
                servidorDisponible = true
                marcarHitoReclamado(context, dia)
            }
            respuesta
        } catch (e: Exception) {
            Log.d(TAG, "reclamar_hito aún no disponible: ${e.message}")
            RespuestaSimple(
                success = false,
                message = "Próximamente: el servidor aún no tiene activadas las recompensas de rachas"
            )
        }
    }
}
