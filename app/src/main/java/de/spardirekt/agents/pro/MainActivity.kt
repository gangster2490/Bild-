package de.spardirekt.agents.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.spardirekt.agents.pro.ui.navigation.VeoPromptProNav
import de.spardirekt.agents.pro.ui.theme.VeoPromptProTheme
import de.spardirekt.agents.pro.ui.theme.VppColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VeoPromptProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VppColors.backgroundLight
                ) {
                    VeoPromptProNav()
                }
            }
        }
    }
}
