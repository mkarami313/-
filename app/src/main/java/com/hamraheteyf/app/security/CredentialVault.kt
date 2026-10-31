package com.hamraheteyf.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.compose.runtime.staticCompositionLocalOf
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the user's sign-in credentials encrypted at rest so they can sign in again with a fingerprint.
 *
 * Two storage generations, so nobody is locked out:
 *  - **v2** (preferred, when strong biometrics are available): hybrid envelope ([CredentialEnvelope]); the
 *    wrapping RSA private key is auth-bound and invalidated by new biometric enrollment, so it can only be
 *    used through a `BiometricPrompt` CryptoObject (see [BiometricGate.prompt]).
 *  - **legacy** (older installs, or devices with only weak biometrics): AES key in the Keystore, with the
 *    biometric prompt as a pure UI gate. Legacy data is silently upgraded to v2 on its first successful load.
 *
 * Every failure path ends in "cleared → ask for the password", and a v2 failure that is not an
 * invalidation permanently falls back to legacy storage on this install.
 */
class CredentialVault(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Credentials decrypted by the biometric step, handed over exactly once to [load]. */
    @Volatile private var unlocked: Pair<String, String>? = null

    init { active = this }

    /** True when the device has a keystore we can use (effectively always on API 24+). */
    fun isSupported(): Boolean = runCatching {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        true
    }.getOrDefault(false)

    fun hasCredentials(): Boolean = prefs.contains(KEY_CT2) || prefs.contains(KEY_CT)

    /** The saved email (not sensitive) so the login field can be prefilled. */
    fun savedEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun save(email: String, password: String) {
        val plain = "$email\n$password"
        unlocked = null
        if (!prefs.getBoolean(KEY_V2_BROKEN, false) && strongBiometricAvailable() && saveV2(email, plain)) return
        saveLegacy(email, plain)
    }

    /**
     * Decrypts and returns (email, password), or null if nothing is saved / decryption fails.
     * For v2 data the secret was already released by [finishUnlock] after the biometric check.
     */
    fun load(): Pair<String, String>? {
        unlocked?.let { unlocked = null; return it }
        if (prefs.contains(KEY_CT2)) return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val ct = prefs.getString(KEY_CT, null) ?: return null
        val creds = runCatching {
            val cipher = Cipher.getInstance(AES)
            cipher.init(Cipher.DECRYPT_MODE, legacyKey(), GCMParameterSpec(128, b64d(iv)))
            CredentialEnvelope.split(String(cipher.doFinal(b64d(ct)), Charsets.UTF_8))
        }.getOrNull() ?: return null
        // Transparent upgrade: re-save (no auth needed to encrypt); stays legacy if v2 is impossible.
        runCatching { save(creds.first, creds.second) }
        return creds
    }

    fun clear() {
        unlocked = null
        prefs.edit().remove(KEY_IV).remove(KEY_CT).remove(KEY_EMAIL).remove(KEY_WK2).remove(KEY_IV2).remove(KEY_CT2).apply()
    }

    // ---------------------------------------------------------------- biometric-bound unlock (v2)

    sealed interface Unlock {
        /** Saved data is legacy; a plain biometric prompt is enough. */
        data object NotNeeded : Unlock
        /** Authenticate with this cipher in a CryptoObject, then call [finishUnlock]. */
        class Needs(val cipher: Cipher) : Unlock
        /** The key is gone/invalidated (new fingerprint enrolled…); vault was cleared, ask for the password. */
        data object Invalid : Unlock
    }

    fun beginUnlock(): Unlock {
        if (!prefs.contains(KEY_CT2)) return Unlock.NotNeeded
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val priv = ks.getKey(RSA_ALIAS, null) as? PrivateKey ?: throw IllegalStateException("no key")
            val cipher = Cipher.getInstance(RSA)
            cipher.init(Cipher.DECRYPT_MODE, priv)
            Unlock.Needs(cipher)
        } catch (e: Exception) {
            clear()
            Unlock.Invalid
        }
    }

    /** Completes a CryptoObject-authenticated unlock. False → vault cleared (and v2 disabled if the key itself misbehaved). */
    fun finishUnlock(authed: Cipher): Boolean {
        return try {
            val sealed = CredentialEnvelope.Sealed(
                b64d(prefs.getString(KEY_WK2, null) ?: error("wk")),
                b64d(prefs.getString(KEY_IV2, null) ?: error("iv")),
                b64d(prefs.getString(KEY_CT2, null) ?: error("ct")),
            )
            val creds = CredentialEnvelope.split(CredentialEnvelope.open(sealed) { authed.doFinal(it) }) ?: error("format")
            unlocked = creds
            true
        } catch (e: Exception) {
            prefs.edit().putBoolean(KEY_V2_BROKEN, true).apply()
            clear()
            false
        }
    }

    // ---------------------------------------------------------------- storage

    private fun strongBiometricAvailable(): Boolean = runCatching {
        BiometricManager.from(app).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    private fun saveV2(email: String, plain: String): Boolean = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        runCatching { ks.deleteEntry(RSA_ALIAS) }  // always a fresh key: recovers from an invalidated one
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(RSA_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        kpg.initialize(spec)
        val pub = kpg.generateKeyPair().public
        val softPub = KeyFactory.getInstance(pub.algorithm).generatePublic(X509EncodedKeySpec(pub.encoded))
        val sealed = CredentialEnvelope.seal(plain) { key ->
            Cipher.getInstance(RSA).apply { init(Cipher.ENCRYPT_MODE, softPub) }.doFinal(key)
        }
        prefs.edit()
            .putString(KEY_WK2, b64(sealed.wrappedKey))
            .putString(KEY_IV2, b64(sealed.iv))
            .putString(KEY_CT2, b64(sealed.ciphertext))
            .putString(KEY_EMAIL, email)
            .remove(KEY_IV).remove(KEY_CT)
            .commit()
        runCatching { ks.deleteEntry(ALIAS) }  // legacy key no longer needed
        true
    }.getOrDefault(false)

    private fun saveLegacy(email: String, plain: String) {
        runCatching {
            val cipher = Cipher.getInstance(AES)
            cipher.init(Cipher.ENCRYPT_MODE, legacyKey())
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_IV, b64(iv))
                .putString(KEY_CT, b64(ct))
                .putString(KEY_EMAIL, email)
                .remove(KEY_WK2).remove(KEY_IV2).remove(KEY_CT2)
                .apply()
        }
    }

    private fun legacyKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
        private const val RSA_ALIAS = "hamrah_cred_rsa_v2"
        private const val AES = "AES/GCM/NoPadding"
        private const val RSA = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding"
        private const val KEY_IV = "iv"
        private const val KEY_CT = "ct"
        private const val KEY_EMAIL = "email"
        private const val KEY_WK2 = "wk2"
        private const val KEY_IV2 = "iv2"
        private const val KEY_CT2 = "ct2"
        private const val KEY_V2_BROKEN = "v2_broken"

        /** The vault instance created by MainActivity; lets [BiometricGate] bind its prompt without a signature change. */
        @Volatile var active: CredentialVault? = null
            private set
    }
}

/** Provides the [CredentialVault] to the Compose tree. Set once in MainActivity. */
val LocalCredentialVault = staticCompositionLocalOf<CredentialVault> {
    error("CredentialVault not provided.")
}
