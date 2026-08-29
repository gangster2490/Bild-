package com.rin.repairagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rin.repairagent.ui.RinNavGraph
import com.rin.repairagent.ui.theme.RinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RinRepairApp
        setContent {
            RinTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RinNavGraph(repository = app.repository)
                }
            }
        }
    }
}
