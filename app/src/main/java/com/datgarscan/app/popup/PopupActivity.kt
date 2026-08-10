package com.datgarscan.app.popup

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.datgarscan.app.databinding.ActivityPopupBinding

class PopupActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TITULO = "extra_titulo"
        private const val EXTRA_MENSAJE = "extra_mensaje"
        private const val EXTRA_TEXTO_BOTON = "extra_texto_boton"
        private const val EXTRA_URL_BOTON = "extra_url_boton"

        fun crearIntent(
            context: Context,
            titulo: String,
            mensaje: String,
            textoBoton: String?,
            urlBoton: String?
        ): Intent {
            return Intent(context, PopupActivity::class.java)
                .putExtra(EXTRA_TITULO, titulo)
                .putExtra(EXTRA_MENSAJE, mensaje)
                .putExtra(EXTRA_TEXTO_BOTON, textoBoton)
                .putExtra(EXTRA_URL_BOTON, urlBoton)
        }
    }

    private lateinit var binding: ActivityPopupBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPopupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val titulo = intent.getStringExtra(EXTRA_TITULO) ?: ""
        val mensaje = intent.getStringExtra(EXTRA_MENSAJE) ?: ""
        val textoBoton = intent.getStringExtra(EXTRA_TEXTO_BOTON)
        val urlBoton = intent.getStringExtra(EXTRA_URL_BOTON)

        binding.tvCerrar.setOnClickListener { finish() }

        com.datgarscan.app.ads.AnunciosManager.ocultarBannersSiCorresponde(this, binding.bannerPopup)

        configurarWebView(titulo, mensaje)

        if (!textoBoton.isNullOrBlank() && !urlBoton.isNullOrBlank()) {
            binding.btnAccion.visibility = View.VISIBLE
            binding.btnAccion.text = textoBoton
            binding.btnAccion.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlBoton)))
                } catch (e: Exception) {
                    Toast.makeText(this, "No se pudo abrir el enlace.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWebView(titulo: String, mensaje: String) {
        binding.webContenido.setBackgroundColor(Color.TRANSPARENT)
        binding.webContenido.settings.javaScriptEnabled = false

        // Los enlaces dentro del contenido se abren en el navegador, no dentro
        // del aviso.
        binding.webContenido.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) { /* enlace invalido, no hacemos nada */ }
                return true
            }
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body {
                    background: transparent;
                    color: #F0EEF2;
                    font-family: system-ui, -apple-system, sans-serif;
                    margin: 0;
                    padding: 20px 24px 8px 24px;
                    line-height: 1.6;
                }
                h1 { font-size: 22px; margin: 0 0 16px 0; color: #FFFFFF; }
                p { margin: 0 0 14px 0; font-size: 15px; }
                a { color: #FF6B00; }
                img { max-width: 100%; height: auto; border-radius: 8px; margin: 8px 0; }
                ul, ol { padding-left: 20px; }
                li { margin-bottom: 8px; }
                strong { color: #FFFFFF; }
                hr { border: 0; border-top: 1px solid #3A2A40; margin: 20px 0; }
            </style>
            </head>
            <body>
                ${if (titulo.isNotBlank()) "<h1>$titulo</h1>" else ""}
                $mensaje
            </body>
            </html>
        """.trimIndent()

        binding.webContenido.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
