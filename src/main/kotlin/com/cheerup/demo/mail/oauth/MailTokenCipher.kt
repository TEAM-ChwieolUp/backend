package com.cheerup.demo.mail.oauth

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class MailTokenCipher(
    private val properties: MailOAuthProperties,
) {
    private val secureRandom = SecureRandom()

    fun encrypt(value: String?): String? {
        if (value == null) {
            return null
        }

        val iv = ByteArray(GCM_IV_BYTE_SIZE)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BIT_SIZE, iv))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        return Base64.getUrlEncoder().withoutPadding().encodeToString(iv + encrypted)
    }

    fun decrypt(value: String?): String? {
        if (value == null) {
            return null
        }

        val payload = Base64.getUrlDecoder().decode(value)
        require(payload.size > GCM_IV_BYTE_SIZE) { "Encrypted mail token payload is invalid." }

        val iv = payload.copyOfRange(0, GCM_IV_BYTE_SIZE)
        val encrypted = payload.copyOfRange(GCM_IV_BYTE_SIZE, payload.size)

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BIT_SIZE, iv))

        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKeySpec {
        val rawKey = properties.tokenEncryptionKey
        if (rawKey.isBlank()) {
            throw BusinessException(ErrorCode.MAIL_TOKEN_ENCRYPTION_NOT_CONFIGURED)
        }

        val keyBytes = Base64.getDecoder().decode(rawKey)
        if (keyBytes.size != AES_256_BYTE_SIZE) {
            throw BusinessException(
                ErrorCode.MAIL_TOKEN_ENCRYPTION_NOT_CONFIGURED,
                detail = "app.mail.oauth.token-encryption-key must be a Base64-encoded 32-byte key.",
            )
        }

        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
        private const val AES_256_BYTE_SIZE = 32
        private const val GCM_IV_BYTE_SIZE = 12
        private const val GCM_TAG_BIT_SIZE = 128
    }
}
