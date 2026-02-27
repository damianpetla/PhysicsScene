package dev.damianpetla.physicsscene

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.damianpetla.physicsscene.demo.DemoCatalogScreen
import dev.damianpetla.physicsscene.ui.theme.PhysicsSceneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PhysicsSceneTheme {
                DemoCatalogScreen()
            }
        }
    }
}
