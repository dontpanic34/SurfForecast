package com.surfcast.surfforecast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import com.surfcast.surfforecast.ui.theme.SurfForecastTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SurfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode = viewModel.themeMode

            val useDarkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            SurfForecastTheme(useDarkTheme = useDarkTheme) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}