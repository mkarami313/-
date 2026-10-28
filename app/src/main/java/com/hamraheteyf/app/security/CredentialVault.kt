package com.hamraheteyf.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.staticCompositionLocalOf
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the user's sign-in credentials encrypted at rest, so they can sign in again with
 * a fingerprint instead of retyping. The AES-256-GCM key lives in the hardware-backed
 * AndroidKeyStore and is non-exportable; the ciphertext in SharedPreferences is useless
 * without it. Using the stored credentials is gated by [BiometricGate] on the login screen.
 *
 * (The key is not itself biometric-bound — that keeps credentials from being wiped every
 * time the user enrolls a new fingerprint, and the biometric prompt still guards every use.)
 */
class CredentialVault(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when the device has a keystore we can use (effectively always on API 24+). */
    fun isSupported(): Boolean = runCatching {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        true
    }.getOrDefault(false)

    fun hasCredentials(): Boolean = prefs.contains(KEY_CT)

    /** The saved email (not sensitive) so the login field can be prefilled. */
    fun savedEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun save(email: String, password: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val ct = cipher.doFinal("$email\n$password".toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_IV, b64(iv))
                .putString(KEY_CT, b64(ct))
                .putString(KEY_EMAIL, email)
                .apply()
        }
    }

    /** Decrypts and returns (email, password), or null if nothing is saved / decryption fails. */
    fun load(): Pair<String, String>? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val ct = prefs.getString(KEY_CT, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, b64d(iv)))
            val plain = String(cipher.doFinal(b64d(ct)), Charsets.UTF_8)
            val nl = plain.indexOf('\n')
            if (nl < 0) null else plain.substring(0, nl) to plain.substring(nl + 1)
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_IV).remove(KEY_CT).remove(KEY_EMAIL).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun b64d(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        private const val PREFS = "hamrah_bio"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "hamrah_cred_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_IV = "iv"
        private const val KEY_CT = "ct"
        private const val KEY_EMAIL = "email"
    }
}

/** Provides the [CredentialVault] to the Compose tree. Set once in MainActivity. */
val LocalCredentialVault = staticCompositionLocalOf<CredentialVault> {
    error("CredentialVault not provided.")
}
