package dev.voltra

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.voltra.ui.VoltraApp
import dev.voltra.ui.VoltraViewModel
import dev.voltra.ui.theme.VoltraTheme
import dev.voltra.ui.theme.shouldUseDarkTheme

class MainActivity : ComponentActivity() {
    private val active = mutableStateOf(false)
    private val viewModel: VoltraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = shouldUseDarkTheme(state.themeMode)
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
                onDispose {}
            }
            VoltraTheme(darkTheme = darkTheme) {
                VoltraApp(viewModel = viewModel, isActive = active.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BackgroundSampling.ensureScheduled(this)
        viewModel.syncSystemStatus()
        active.value = true
    }

    override fun onPause() {
        active.value = false
        super.onPause()
    }
}
