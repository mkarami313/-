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

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
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
            .setAllowedAuthenticators(ALLOWED)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }
}
