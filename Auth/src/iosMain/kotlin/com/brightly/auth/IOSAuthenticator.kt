@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.brightly.auth

import kotlinx.cinterop.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.Foundation.*
import platform.LocalAuthentication.*
import platform.UIKit.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private var enabledInMemoryIos: Boolean = false

class IOSAuthenticator : PlatformAuthenticator {

    override fun canAuthenticate(): Boolean {
        val ctx = LAContext()
        return ctx.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)
    }

    override suspend fun isPermissionGranted(): Boolean {
        val ctx = LAContext()
        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            return ctx.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, err.ptr)
        }
    }

    override suspend fun requestPermission(): Boolean = isPermissionGranted()

    override fun openBiometricSettings() {
        val url = NSURL.URLWithString("App-Prefs:root=FaceID") ?: return
        val app = UIApplication.sharedApplication

        if (app.canOpenURL(url)) {
            app.openURL(url, emptyMap<Any?, Any?>(), null)
        } else {
            openAppSettings()
        }
    }

    override fun openAppSettings() {
        val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        val app = UIApplication.sharedApplication

        if (app.canOpenURL(settingsUrl)) {
            app.openURL(settingsUrl, emptyMap<Any?, Any?>(), null)
        }
    }

    override suspend fun getBiometricType(): BiometricType {
        val ctx = LAContext()
        return when (ctx.biometryType) {
            LABiometryTypeFaceID -> BiometricType.FACE_ID
            LABiometryTypeTouchID -> BiometricType.TOUCH_ID
            else -> BiometricType.NONE
        }
    }

    override suspend fun authenticate(
        reason: String,
        requireCrypto: Boolean
    ): AuthResult = suspendCoroutine { cont ->

        var resumed = false
        fun safeResume(result: AuthResult) {
            if (!resumed) {
                resumed = true
                cont.resume(result)
            }
        }

        val context = LAContext()

        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val canEvaluate = context.canEvaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                errorPtr.ptr
            )

            if (!canEvaluate) {
                val error = errorPtr.value
                runOnMain { showPermissionDeniedAlert() }
                safeResume(AuthResult.Failure(error?.localizedDescription ?: Constant.BIOMETRIC_UNAVAILABLE))
                return@suspendCoroutine
            }
        }

        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthentication,
            localizedReason = reason
        ) { success, error ->

            if (resumed) return@evaluatePolicy

            if (success) {
                safeResume(AuthResult.Success)
                return@evaluatePolicy
            }

            val nsError = error as NSError?
            when (nsError?.code?.toInt()) {

                LAErrorUserCancel.toInt() ->
                    safeResume(AuthResult.Cancelled)

                LAErrorBiometryLockout.toInt() -> {
                    runOnMain { showBiometricSetupAlert() }
                    safeResume(AuthResult.Failure(Constant.BIOMETRIC_LOCKED))
                }

                LAErrorNotInteractive.toInt() -> {
                    runOnMain { showPermissionDeniedAlert() }
                    safeResume(AuthResult.Failure(Constant.PERMISSION_DENIED))
                }

                else ->
                    safeResume(AuthResult.Failure(nsError?.localizedDescription ?: Constant.BIOMETRIC_FAILED))
            }
        }
    }

    override suspend fun authenticateForSessionRefresh(reason: String): AuthResult {
        return authenticate(reason, requireCrypto = false)
    }

    override suspend fun enableBiometricAuth(): Boolean {
        enabledInMemoryIos = true
        return enabledInMemoryIos
    }

    override suspend fun disableBiometricAuth() {
        enabledInMemoryIos = false
    }

    override suspend fun isEnabled(): Boolean = enabledInMemoryIos

    override suspend fun signData(data: ByteArray): ByteArray? = null

    override suspend fun debugBiometricState() {
        val ctx = LAContext()
        NSLog(
            "IOSAuthenticator debug: canEvaluate=%s, biometryType=%s, enabledInMemory=%s",
            ctx.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null).toString(),
            ctx.biometryType.toString(),
            enabledInMemoryIos.toString()
        )
    }

    private fun runOnMain(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { block() }
    }

    private fun showPermissionDeniedAlert() {
        showAlert(
            title = Constant.BIOMETRIC_PERMISSION_DENIED_TITLE,
            message = Constant.BIOMETRIC_PERMISSION_DENIED_MESSAGE,
            button = Constant.OPEN_SETTINGS
        ) {
            openAppSettings()
        }
    }

    private fun showBiometricSetupAlert() {
        showAlert(
            title = Constant.BIOMETRIC_SETUP_TITLE,
            message = Constant.BIOMETRIC_SETUP_MESSAGE,
            button = Constant.OPEN_SETTINGS
        ) {
            openAppSettings()
        }
    }

    private fun showAlert(
        title: String,
        message: String,
        button: String,
        onClick: () -> Unit
    ) {
        val controller = UIAlertController.alertControllerWithTitle(
            title,
            message,
            UIAlertControllerStyleAlert
        )

        controller.addAction(
            UIAlertAction.actionWithTitle(button, UIAlertActionStyleDefault) { _ -> onClick() }
        )

        controller.addAction(
            UIAlertAction.actionWithTitle(Constant.CANCEL, UIAlertActionStyleCancel, null)
        )

        presentAlert(controller)
    }

    private fun presentAlert(alert: UIAlertController) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        val top = root.presentedViewController ?: root
        top.presentViewController(alert, animated = true, completion = null)
    }
}
