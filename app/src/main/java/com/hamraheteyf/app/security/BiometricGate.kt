package com.hamraheteyf.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Thin wrapper over AndroidX [BiometricPrompt] used as an unlock gate for saved credentials. */
object BiometricGate {

    private const val ALLOWED = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    /** True when the device has an enrolled fingerprint/face we can prompt for. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the unlock prompt. When [vault] holds biometric-bound (v2) credentials the prompt is created with a
     * CryptoObject, and on success the secret is already released into the vault for the following `vault.load()`.
     * Legacy data uses a plain prompt. If the bound key is gone/invalidated the vault is cleared and [onError]
     * explains that the password is needed (the next password login with "remember" re-enrolls).
     */
    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        vault: CredentialVault? = CredentialVault.active,
    ) {
        val unlock = vault?.beginUnlock() ?: CredentialVault.Unlock.NotNeeded
        if (unlock is CredentialVault.Unlock.Invalid) {
            onError("اثر انگشت یا قفل دستگاه تغییر کرده است؛ لطفاً یک بار با رمز وارد شوید تا دوباره ذخیره شود.")
            return
        }
        val crypto = (unlock as? CredentialVault.Unlock.Needs)?.cipher
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (crypto != null) {
                    val authed = result.cryptoObject?.cipher
                    if (authed == null || vault?.finishUnlock(authed) != true) {
                        onError("خواندن مشخصات ذخیره‌شده ممکن نشد؛ لطفاً با رمز وارد شوید.")
                        return
                    }
                }
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Cancellations are not real errors; stay quiet so the user can just type instead.
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) onError(errString.toString())
            }
            // onAuthenticationFailed: a single bad read; the prompt stays open, so ignore it.
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود با اثر انگشت")
            .setSubtitle("برای ورود به «همراهِ طیف» احراز هویت کنید")
            .setNegativeButtonText("ورود با رمز")
            .setAllowedAuthenticators(if (crypto != null) BIOMETRIC_STRONG else ALLOWED)
            .setConfirmationRequired(false)
            .build()
        runCatching {
            if (crypto != null) prompt.authenticate(info, BiometricPrompt.CryptoObject(crypto)) else prompt.authenticate(info)
        }.onFailure { onError("ورود با اثر انگشت در دسترس نیست؛ با رمز وارد شوید.") }
    }
}
