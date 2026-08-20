package com.datgarscan.app.descargas

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra/descifra las paginas descargadas con AES-256/GCM usando una llave
 * generada dentro del Android Keystore del dispositivo. La llave nunca sale
 * en texto plano ni se guarda en un archivo: vive protegida por el sistema,
 * y solo esta app puede usarla para cifrar/descifrar. Si alguien copia los
 * archivos .enc a otro dispositivo (o los mira con un explorador de
 * archivos), son bytes sin sentido sin acceso a esa llave.
 */
object CifradorDescargas {

    private const val ALIAS = "datgarscan_descargas_key"
    private const val PROVEEDOR = "AndroidKeyStore"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val TAMANO_TAG_GCM = 128 // bits

    private fun obtenerLlave(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVEEDOR)
        keyStore.load(null)

        val existente = keyStore.getKey(ALIAS, null) as? SecretKey
        if (existente != null) return existente

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generador.init(spec)
        return generador.generateKey()
    }

    /** Cifra [datos] y devuelve iv (12 bytes) + ciphertext, listos para escribir a disco. */
    fun cifrar(datos: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerLlave())
        val iv = cipher.iv
        val cifrado = cipher.doFinal(datos)
        return iv + cifrado
    }

    /** Recibe lo que devuelve [cifrar] (iv + ciphertext) y regresa los bytes originales. */
    fun descifrar(paquete: ByteArray): ByteArray {
        val iv = paquete.copyOfRange(0, 12)
        val ciphertext = paquete.copyOfRange(12, paquete.size)
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.DECRYPT_MODE, obtenerLlave(), GCMParameterSpec(TAMANO_TAG_GCM, iv))
        return cipher.doFinal(ciphertext)
    }
}
