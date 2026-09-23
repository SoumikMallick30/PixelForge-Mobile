package dev.pixelforge.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.pixelforge.mobile.ui.PixelForgeApp
import dev.pixelforge.mobile.ui.PixelForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PixelForgeTheme { PixelForgeApp() } }
    }
}
