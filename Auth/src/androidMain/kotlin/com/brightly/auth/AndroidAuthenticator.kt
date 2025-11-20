// androidMain/kotlin/com/brightly/auth/AndroidAuthenticator.kt
package com.brightly.auth

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.coroutines.resume
//
//private const val TAG = "AndroidAuthenticator"
//private const val KEY_NAME = "com.brightly.biometric_key_v1"
//private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"

class AndroidAuthenticator(
    private val activity: FragmentActivity
) : PlatformAuthenticator {

    // In-memory flag (mode B) — reset on process kill
    private var enabledInMemory: Boolean = false

    override fun canAuthenticate(): Boolean {
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun isPermissionGranted(): Boolean {
        // On Android biometric permission is a normal permission; declare in manifest.
        // The runtime "permission" isn't required — but the device/app must be configured.
        return canAuthenticate()
    }

    override suspend fun requestPermission(): Boolean {
        // There's no runtime permission to request for biometrics.
        // We can guide user to enroll via settings if not enrolled.
        if (canAuthenticate()) return true
        openBiometricSettings()
        return false
    }

    override fun openBiometricSettings() {
        val intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)
        intent.putExtra(
            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
        // Activity may not be flagged as new task; delegate to activity
        try {
            activity.startActivity(intent)
        } catch (ex: Exception) {
            // fallback to app settings
            openAppSettings()
        }
    }

    override fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = android.net.Uri.fromParts("package", activity.packageName, null)
        activity.startActivity(intent)
    }

    override suspend fun getBiometricType(): BiometricType {
        // Best-effort: Android doesn't expose Face vs Fingerprint reliably across devices.
        // We'll return FINGERPRINT if hardware supports fingerprint; otherwise NONE.
        val manager = BiometricManager.from(activity)
        val status = manager.canAuthenticate()
        return when (status) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // We can't introspect type reliably here so return FINGERPRINT as default
                BiometricType.FINGERPRINT
            }
            else -> BiometricType.NONE
        }
    }

    // --- Authentication flow
    override suspend fun authenticate(reason: String, requireCrypto: Boolean): AuthResult =
        suspendCancellableCoroutine { cont ->
            val manager = BiometricManager.from(activity)
            when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    // proceed
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                    // show enroll prompt (activity/UI) — we only provide method to open settings
                    openBiometricSettings()
                    cont.resume(AuthResult.Failure(Constant.BiometricEnrolled))
                    return@suspendCancellableCoroutine
                }
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                    cont.resume(AuthResult.Failure(Constant.BiometricNotAvailable))
                    return@suspendCancellableCoroutine
                }
                else -> {
                    cont.resume(AuthResult.Failure(Constant.BiometricUnAvailable))
                    return@suspendCancellableCoroutine
                }
            }

            val executor = ContextCompat.getMainExecutor(activity)
            val promptInfo = BiometricPrompt.PromptInfo.Builder() .setTitle(reason)
                .setSubtitle(Constant.BiometricSubTitle)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                            or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            // Optionally handle cryptoObject. Keep simple for mode B: not using a persistent key by default.
            var cryptoObject: BiometricPrompt.CryptoObject? = null
            if (requireCrypto) {
                try {
                    val cipher = initCipherForEncrypt()
                    cryptoObject = BiometricPrompt.CryptoObject(cipher)
                } catch (e: Exception) {
                    Log.w(Constant.TAG, "Cipher init failed, falling back to non-crypto auth: ${e.message}", e)
                    // Optionally wipe invalid key if needed
                    try {
                        val ks = KeyStore.getInstance(Constant.AndroidKeyStore).apply { load(null) }
                        if (ks.containsAlias(Constant.KEY_NAME)) {
                            ks.deleteEntry(Constant.KEY_NAME)
                        }
                    } catch (_: Exception) {}
                    cryptoObject = null
                }
            }

            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try {
                        if (requireCrypto && result.cryptoObject?.cipher != null) {
                            val cipher = result.cryptoObject?.cipher!!
                            val encrypted = cipher.doFinal("auth_ok".toByteArray())
                            cont.resume(AuthResult.CryptoSuccess(encrypted))
                        } else {
                            cont.resume(AuthResult.Success)
                        }
                    } catch (ex: Exception) {
                        cont.resume(AuthResult.Failure("Crypto op failed: ${ex.message}"))
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        cont.resume(AuthResult.Cancelled)
                    } else {
                        cont.resume(AuthResult.Failure(errString.toString()))
                    }
                }

                override fun onAuthenticationFailed() {
                    // ignore — allow retries
                }
            })

            try {
                if (cryptoObject != null) prompt.authenticate(promptInfo, cryptoObject)
                else prompt.authenticate(promptInfo)
            } catch (e: Exception) {
                cont.resume(AuthResult.Failure("Failed to start biometric prompt: ${e.message}"))
            }
        }

    override suspend fun authenticateForSessionRefresh(reason: String): AuthResult {
        // For session refresh we behave same as authenticate but with a different reason
        return authenticate(reason, requireCrypto = false)
    }

    // --- Enable/Disable (in-memory)
    override suspend fun enableBiometricAuth(): Boolean {
        enabledInMemory = true
        return enabledInMemory
    }

    override suspend fun disableBiometricAuth() {
        enabledInMemory = false
    }

    override suspend fun isEnabled(): Boolean = enabledInMemory

    override suspend fun signData(data: ByteArray): ByteArray? {
        // Implement signing via a stored EC key if you want; simplified here
        return null
    }

    override suspend fun debugBiometricState() {
        Log.d(Constant.TAG, "canAuthenticate=${canAuthenticate()} enabledInMemory=$enabledInMemory")
    }

    // --- Helpers for crypto key/cipher (optional)
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(Constant.KEY_NAME)) {
            val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                Constant.KEY_NAME,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(-1)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
        return keyStore.getKey(Constant.KEY_NAME, null) as SecretKey
    }

    private fun initCipherForEncrypt(): Cipher {
        val cipher = Cipher.getInstance(Constant.TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        return cipher
    }
}
