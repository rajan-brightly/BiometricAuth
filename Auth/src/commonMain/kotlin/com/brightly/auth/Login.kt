package com.brightly.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(authenticator: PlatformAuthenticator) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Waiting…") }
    var type by remember { mutableStateOf("") }
    var isEnableBioMetric by remember { mutableStateOf(false) }
    Switch(
        checked = isEnableBioMetric, onCheckedChange = { newState ->
            isEnableBioMetric = newState


            if (isEnableBioMetric){
                CoroutineScope(Dispatchers.Main).launch {
                    authenticator.enableBiometricAuth()
                }
            }else{
                CoroutineScope(Dispatchers.Main).launch {
                    authenticator.disableBiometricAuth()
                }
            }
        }
    )
    Button(onClick = {
        scope.launch {

                val res = authenticator.authenticate("Please authenticate", requireCrypto = true)
                status = res.toString()


        }
    }) { Text("Authenticate") }
   scope.launch {
       type = "${authenticator.getBiometricType()}"
      // isEnableBioMetric = authenticator.isEnabled()
   }
   Text(type)
}