package com.dokacam.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dokacam.camera.ui.camera.CameraScreen
import com.dokacam.camera.ui.gallery.GalleryScreen
import com.dokacam.camera.ui.settings.SettingsScreen
import com.dokacam.camera.ui.theme.DokaCamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DokaCamTheme {
                DokaNavHost()
            }
        }
    }
}

@Composable
private fun DokaNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "camera") {
        composable("camera") {
            CameraScreen()
        }
        composable("gallery") {
            GalleryScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
