package com.datgarscan.app.lector

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    var onZoomChanged: ((estaAmpliada: Boolean) -> Unit)? = null
    var onTap: (() -> Unit)? = null

    private val matrizImagen = Matrix()
    private var escalaActual = 1f
    private val escalaMinima = 1f
    private val escalaMaxima = 4f

    private var ultimoX = 0f
    private var ultimoY = 0f

    private val detectorEscala = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val nuevaEscala = (escalaActual * detector.scaleFactor).coerceIn(escalaMinima, escalaMaxima)
            val factor = nuevaEscala / escalaActual
            escalaActual = nuevaEscala
            matrizImagen.postScale(factor, factor, detector.focusX, detector.focusY)
            aplicarMatriz()
            return true
        }
    })

    private val detectorGestos = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            restablecerZoom()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTap?.invoke()
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        setOnTouchListener { _, event ->
            detectorEscala.onTouchEvent(event)
            detectorGestos.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ultimoX = event.x
                    ultimoY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (escalaActual > escalaMinima && event.pointerCount == 1) {
                        val dx = event.x - ultimoX
                        val dy = event.y - ultimoY
                        matrizImagen.postTranslate(dx, dy)
                        aplicarMatriz()
                        ultimoX = event.x
                        ultimoY = event.y
                    }
                }
            }
            true
        }
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        restablecerZoom()
    }

    private fun restablecerZoom() {
        escalaActual = 1f
        matrizImagen.reset()
        centrarImagen()
        aplicarMatriz()
        onZoomChanged?.invoke(false)
    }

    private fun centrarImagen() {
        val d = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (vw == 0f || vh == 0f || dw <= 0f || dh <= 0f) return

        val escala = min(vw / dw, vh / dh)
        val dx = (vw - dw * escala) / 2f
        val dy = (vh - dh * escala) / 2f
        matrizImagen.setScale(escala, escala)
        matrizImagen.postTranslate(dx, dy)
    }

    private fun aplicarMatriz() {
        imageMatrix = matrizImagen
        onZoomChanged?.invoke(escalaActual > escalaMinima + 0.01f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centrarImagen()
        aplicarMatriz()
    }
}
