package com.brightly.auth

interface PlatformAuthenticator {

    // --- Permission & Capability ----
    fun canAuthenticate(): Boolean
    suspend fun isPermissionGranted(): Boolean
    suspend fun requestPermission(): Boolean
    fun openBiometricSettings()
    fun openAppSettings()

    // --- Biometry Info ----
    suspend fun getBiometricType(): BiometricType

    // --- Normal Authentication ----
    suspend fun authenticate(
        reason: String = "Authenticate",
        requireCrypto: Boolean = false
    ): AuthResult

    // --- Session Refresh Authentication ----
    suspend fun authenticateForSessionRefresh(reason: String = "Re-authenticate"): AuthResult

    // --- Enable / Disable support ----
    suspend fun enableBiometricAuth(): Boolean
    suspend fun disableBiometricAuth()
    suspend fun isEnabled(): Boolean

    // --- Crypto Signing ---
    suspend fun signData(data: ByteArray): ByteArray?

    // --- Debugging ---
    suspend fun debugBiometricState()
}

enum class BiometricType {
    FACE_ID,
    TOUCH_ID,
    FINGERPRINT,
    NONE
}
