package com.datorio.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts the passwords of data sources at rest (AES-256-GCM). The key is DATASOURCE_ENCRYPTION_KEY,
 * or the JWT secret when it is not set, hashed to 256 bits. A stored value without the prefix is
 * plain text from an installation older than this cipher and is read as it is; it is encrypted the
 * next time the data source is saved.
 */
@Component
class SecretCipher(
    @Value("\${datorio.security.datasource-encryption-key:}") encryptionKey: String,
    @Value("\${datorio.jwt.secret}") jwtSecret: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val key = SecretKeySpec(
        MessageDigest.getInstance("SHA-256").digest((encryptionKey.ifBlank { jwtSecret }).toByteArray(Charsets.UTF_8)),
        "AES"
    )
    private val random = SecureRandom()

    init {
        if (encryptionKey.isBlank()) {
            log.info("DATASOURCE_ENCRYPTION_KEY is not set: data source passwords are encrypted with a key derived from JWT_SECRET")
        }
    }

    fun encrypt(plain: String?): String? {
        if (plain == null) return null
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(stored: String?): String? {
        if (stored == null || !stored.startsWith(PREFIX)) return stored
        val bytes = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return try {
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException("Cannot decrypt a data source password: DATASOURCE_ENCRYPTION_KEY or JWT_SECRET changed since it was saved; enter the password again", e)
        }
    }

    fun isEncrypted(stored: String?): Boolean = stored?.startsWith(PREFIX) == true

    companion object {
        const val PREFIX = "enc:v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
