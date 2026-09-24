package com.surfcast.surfforecast

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.surfcast.surfforecast.ui.theme.AppColors

/**
 * Écran de démarrage (Tâche 1) : affiché tant que le SurfViewModel est dans l'état
 * SurfUiState.Loading, c'est-à-dire pendant le chargement des données initiales du
 * spot actif. MainScreen.kt gère la transition en fondu (Crossfade) vers le contenu
 * principal une fois le chargement terminé.
 *
 * Fond fixe (AppColors.DarkBackground) quel que soit le thème clair/sombre du système :
 * le logo de l'app (ic_launcher_foreground) est blanc, donc un fond toujours sombre
 * garantit un contraste correct dans tous les cas.
 */
@Composable
fun SplashScreen() {
    val entrance = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(entrance.value)
                .scale(0.9f + 0.1f * entrance.value)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontSize = 26.sp
            )
        }
    }
}