package com.brightly.biometriclocalauth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
//import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import com.brightly.auth.AndroidAuthenticator

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(authenticator = AndroidAuthenticator(this))
        }
    }
}

//@Preview
//@Composable
//fun AppAndroidPreview() {
//    App()
//}