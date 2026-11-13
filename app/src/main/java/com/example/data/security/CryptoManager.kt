package com.example.data.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val ITERATION_COUNT = 10000
    private const val KEY_LENGTH_BIT = 256

    // Default internal app salt for device-level field encryption
    private val INTERNAL_SALT = byteArrayOf(
        0x43, 0x6f, 0x6e, 0x74, 0x61, 0x63, 0x74, 0x73,
        0x50, 0x72, 0x6f, 0x56, 0x61, 0x75, 0x6c, 0x74
    )

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BIT)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plain text using a custom passphrase or master app key.
     * Returns Base64 encoded string containing Salt + IV + Ciphertext.
     */
    fun encrypt(plainText: String, passphrase: String = "AlAsmaDefaultSecureVaultKey2026"): String {
        if (plainText.isEmpty()) return ""
        try {
            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH_BYTE)
            random.nextBytes(salt)

            val iv = ByteArray(IV_LENGTH_BYTE)
            random.nextBytes(iv)

            val secretKey = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))

            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Combine Salt (16B) + IV (12B) + EncryptedBytes
            val combined = ByteArray(salt.size + iv.size + encryptedBytes.size)
            System.arraycopy(salt, 0, combined, 0, salt.size)
            System.arraycopy(iv, 0, combined, salt.size, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, salt.size + iv.size, encryptedBytes.size)

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return plainText
        }
    }

    /**
     * Decrypts a Base64 encoded payload using the corresponding passphrase.
     */
    fun decrypt(encryptedPayload: String, passphrase: String = "AlAsmaDefaultSecureVaultKey2026"): String {
        if (encryptedPayload.isEmpty()) return ""
        try {
            val combined = Base64.decode(encryptedPayload, Base64.NO_WRAP)
            if (combined.size < SALT_LENGTH_BYTE + IV_LENGTH_BYTE) {
                return encryptedPayload
            }

            val salt = ByteArray(SALT_LENGTH_BYTE)
            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH_BYTE)

            val iv = ByteArray(IV_LENGTH_BYTE)
            System.arraycopy(combined, SALT_LENGTH_BYTE, iv, 0, IV_LENGTH_BYTE)

            val cipherTextSize = combined.size - SALT_LENGTH_BYTE - IV_LENGTH_BYTE
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, SALT_LENGTH_BYTE + IV_LENGTH_BYTE, cipherText, 0, cipherTextSize)

            val secretKey = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))

            val decryptedBytes = cipher.doFinal(cipherText)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails or wrong password
            return "[محتوى مشفر - كلمة مرور غير صحيحة]"
        }
    }

    /**
     * Verifies if a given password can correctly decrypt an encrypted test probe
     */
    fun verifyPassword(encryptedProbe: String, attempt: String): Boolean {
        return try {
            val decrypted = decrypt(encryptedProbe, attempt)
            decrypted == "VERIFY_TOKEN_OK"
        } catch (e: Exception) {
            false
        }
    }

    fun createVerificationProbe(passphrase: String): String {
        return encrypt("VERIFY_TOKEN_OK", passphrase)
    }
}
