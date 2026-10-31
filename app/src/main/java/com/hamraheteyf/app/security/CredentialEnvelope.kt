package com.hamraheteyf.app.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hybrid envelope for saved credentials: a fresh AES-256-GCM key encrypts "email\npassword", and that
 * small key is wrapped by an RSA key whose private half is biometric-bound in the Keystore. Pure JVM,
 * so the format is unit-testable without a device.
 */
internal object CredentialEnvelope {
    class Sealed(val wrappedKey: ByteArray, val iv: ByteArray, val ciphertext: ByteArray)

    fun seal(plain: String, wrap: (ByteArray) -> ByteArray): Sealed {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Sealed(wrap(key), iv, ct)
    }

    fun open(s: Sealed, unwrap: (ByteArray) -> ByteArray): String {
        val key = unwrap(s.wrappedKey)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, s.iv))
        return String(c.doFinal(s.ciphertext), Charsets.UTF_8)
    }

    /** "email\npassword" → (email, password); null when malformed. */
    fun split(plain: String): Pair<String, String>? {
        val nl = plain.indexOf('\n')
        return if (nl < 0) null else plain.substring(0, nl) to plain.substring(nl + 1)
    }
}
