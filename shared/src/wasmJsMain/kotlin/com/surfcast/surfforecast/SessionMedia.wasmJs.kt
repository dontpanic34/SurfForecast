package com.surfcast.surfforecast

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

// Version web : pas encore de photo/vidéo jointe aux sessions (bouton masqué).
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
