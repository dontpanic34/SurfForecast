package com.surfcast.surfforecast

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

// Sélecteur de photos iOS (PHPickerViewController) : étape suivante du portage.
@Composable
actual fun rememberMediaPicker(onPicked: (String) -> Unit): (() -> Unit)? = null

@Composable
actual fun SessionMediaView(mediaUri: String) {
    Text(
        text = "📷 Média joint",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
}
