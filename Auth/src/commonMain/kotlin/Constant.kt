
object Constant{
    const val TAG = "AndroidAuthenticator"
    const val KEY_NAME = "com.brightly.biometric_key_v1"
    const val TRANSFORMATION = "AES/CBC/PKCS7Padding"
    const val BiometricEnrolled = "Biometric not enrolled"
    const val BiometricNotAvailable = "Biometric not available"
    const val BiometricUnAvailable = "Biometric unavailable"
    const val BiometricSubTitle = "Authenticate with biometric or screen lock"
    const val AndroidKeyStore = "AndroidKeyStore"

    // Biometric labels/messages shared across iOS + Android
    const val BIOMETRIC_PERMISSION_DENIED_TITLE = "Biometric Permission Denied"
    const val BIOMETRIC_PERMISSION_DENIED_MESSAGE = "Please allow Face ID / Touch ID in Settings."
    const val OPEN_SETTINGS = "Open Settings"

    const val BIOMETRIC_SETUP_TITLE = "Biometrics Not Set Up"
    const val BIOMETRIC_SETUP_MESSAGE = "You need to set up Face ID / Touch ID to continue."

    const val CANCEL = "Cancel"

    const val BIOMETRIC_LOCKED = "Biometry locked"
    const val BIOMETRIC_FAILED = "Failed"
    const val BIOMETRIC_UNAVAILABLE = "Unavailable"
    const val PERMISSION_DENIED = "Permission denied"

    // General strings
    const val DEFAULT_AUTH_REASON = "Authenticate to continue"

}