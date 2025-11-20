package com.brightly.biometriclocalauth

import androidx.compose.ui.window.ComposeUIViewController
import com.brightly.auth.IOSAuthenticator

fun MainViewController() = ComposeUIViewController { App(authenticator = IOSAuthenticator()) }