package com.surfcast.surfforecast

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.surfcast.surfforecast.resources.Res
import com.surfcast.surfforecast.resources.ic_launcher_foreground
import com.surfcast.surfforecast.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource

/**
 * Écran de démarrage (Tâche 1) : affiché tant que le SurfViewModel est dans l'état
 * SurfUiState.Loading, c'est-à-dire pendant le chargement des données initiales du
 * spot actif. MainScreen.kt gère la transition en fondu (Crossfade) vers le contenu
 * principal une fois le chargement terminé.
 *
 * Fond fixe (AppColors.DarkBackground) quel que soit le thème clair/sombre du système,
 * pour rester cohérent avec le fond du logo (ic_launcher_foreground).
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

    // Après 8 s, on dit que ça charge encore : sur une connexion lente, le logo seul faisait croire à un écran figé.
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(8000); slow = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBackground),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .alpha(entrance.value)
                .scale(0.9f + 0.1f * entrance.value)
        )
        Text(
            text = if (slow) tr("La connexion est lente, ça charge encore…") else tr("Chargement des prévisions…"),
            fontSize = 13.sp, color = Color(0xFF90A4AE), modifier = Modifier.padding(top = 10.dp)
        )
    }
}
