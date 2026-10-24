package io.github.akrishna87.semippu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.akrishna87.semippu.ui.SemippuRoot
import io.github.akrishna87.semippu.ui.SemippuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SemippuTheme { SemippuRoot() }
        }
    }
}
