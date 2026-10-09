package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Icônes météo et drapeaux dessinés par l'appli : les émojis dépendent de la police du téléphone (soleil blanc sur
// iPhone, couleurs différentes ailleurs), ces dessins sont identiques sur Android, iPhone et le site.

private val SunYellow = Color(0xFFFFC107)
private val CloudGrey = Color(0xFFB0BEC5)
private val CloudDark = Color(0xFF78909C)
private val RainBlue = Color(0xFF4FC3F7)
private val MoonCream = Color(0xFFFFE082)

private fun DrawScope.sun(c: Offset, r: Float) {
    val ray = Stroke(width = r * 0.28f, cap = StrokeCap.Round)
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f
        drawLine(SunYellow, Offset(c.x + cos(a) * r * 1.35f, c.y + sin(a) * r * 1.35f), Offset(c.x + cos(a) * r * 1.8f, c.y + sin(a) * r * 1.8f), strokeWidth = ray.width, cap = StrokeCap.Round)
    }
    drawCircle(SunYellow, radius = r, center = c)
}

private fun DrawScope.moon(c: Offset, r: Float) {
    drawCircle(MoonCream, radius = r, center = c)
    drawCircle(Color.Black.copy(alpha = 0f), radius = r, center = c)
    // Croissant : disque décalé en transparence
    val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r * 0.1f, c.y - r * 1.1f, c.x + r * 1.7f, c.y + r * 0.9f)) }
    drawPath(cut, color = Color(0xFF132836))
}

private fun DrawScope.cloud(x: Float, y: Float, w: Float, color: Color) {
    val h = w * 0.55f
    drawCircle(color, radius = h * 0.42f, center = Offset(x + w * 0.3f, y + h * 0.55f))
    drawCircle(color, radius = h * 0.55f, center = Offset(x + w * 0.55f, y + h * 0.4f))
    drawCircle(color, radius = h * 0.4f, center = Offset(x + w * 0.78f, y + h * 0.58f))
    drawRoundRect(color, topLeft = Offset(x + w * 0.2f, y + h * 0.55f), size = Size(w * 0.7f, h * 0.43f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.2f))
}

private fun DrawScope.drops(x: Float, y: Float, w: Float, n: Int) {
    for (i in 0 until n) {
        val dx = x + w * (0.28f + 0.22f * i)
        drawLine(RainBlue, Offset(dx, y), Offset(dx - w * 0.06f, y + w * 0.2f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.snow(x: Float, y: Float, w: Float) {
    for (i in 0 until 3) drawCircle(Color.White, radius = w * 0.05f, center = Offset(x + w * (0.3f + 0.2f * i), y + w * (0.08f + 0.1f * (i % 2))))
}

/** Icône météo dessinée à partir de l'ancien émoji (☀️ 🌙 🌤️ ☁️ 🌫️ 🌧️ 🌦️ ❄️ ⛈️) : mêmes sens, même rendu partout. */
@Composable
fun WeatherIcon(emoji: String, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        when {
            emoji.startsWith("☀") -> sun(Offset(s / 2, s / 2), s * 0.24f)
            emoji.startsWith("🌙") -> moon(Offset(s * 0.5f, s * 0.5f), s * 0.3f)
            emoji.startsWith("🌤") -> { sun(Offset(s * 0.34f, s * 0.36f), s * 0.17f); cloud(s * 0.18f, s * 0.36f, s * 0.8f, CloudGrey) }
            emoji.startsWith("🌦") -> { sun(Offset(s * 0.34f, s * 0.28f), s * 0.14f); cloud(s * 0.18f, s * 0.26f, s * 0.8f, CloudGrey); drops(s * 0.18f, s * 0.72f, s * 0.8f, 3) }
            emoji.startsWith("🌧") -> { cloud(s * 0.1f, s * 0.18f, s * 0.8f, CloudDark); drops(s * 0.1f, s * 0.68f, s * 0.8f, 3) }
            emoji.startsWith("⛈") -> {
                cloud(s * 0.1f, s * 0.15f, s * 0.8f, CloudDark)
                val bolt = Path().apply {
                    moveTo(s * 0.52f, s * 0.58f); lineTo(s * 0.4f, s * 0.78f); lineTo(s * 0.5f, s * 0.78f); lineTo(s * 0.44f, s * 0.95f); lineTo(s * 0.62f, s * 0.7f); lineTo(s * 0.52f, s * 0.7f); close()
                }
                drawPath(bolt, SunYellow)
            }
            emoji.startsWith("❄") -> { cloud(s * 0.1f, s * 0.15f, s * 0.8f, CloudGrey); snow(s * 0.1f, s * 0.68f, s * 0.8f) }
            emoji.startsWith("🌫") -> {
                for (i in 0 until 3) drawLine(CloudGrey, Offset(s * 0.15f, s * (0.3f + 0.2f * i)), Offset(s * 0.85f, s * (0.3f + 0.2f * i)), strokeWidth = s * 0.09f, cap = StrokeCap.Round)
            }
            else -> cloud(s * 0.1f, s * 0.2f, s * 0.8f, CloudGrey) // ☁️
        }
    }
}

/** Drapeau dessiné (3:2) : fr, en (Union Jack simplifié), de, es. */
@Composable
fun FlagIcon(code: String, height: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = height * 1.5f, height = height)) {
        val w = size.width
        val h = size.height
        when (code) {
            "fr" -> {
                drawRect(Color(0xFF0055A4), Offset.Zero, Size(w / 3, h))
                drawRect(Color.White, Offset(w / 3, 0f), Size(w / 3, h))
                drawRect(Color(0xFFEF4135), Offset(2 * w / 3, 0f), Size(w / 3, h))
            }
            "de" -> {
                drawRect(Color.Black, Offset.Zero, Size(w, h / 3))
                drawRect(Color(0xFFDD0000), Offset(0f, h / 3), Size(w, h / 3))
                drawRect(Color(0xFFFFCE00), Offset(0f, 2 * h / 3), Size(w, h / 3))
            }
            "es" -> {
                drawRect(Color(0xFFAA151B), Offset.Zero, Size(w, h))
                drawRect(Color(0xFFF1BF00), Offset(0f, h * 0.25f), Size(w, h * 0.5f))
            }
            else -> { // en : fond bleu, croix de Saint-André blanche et rouge, croix de Saint-Georges
                drawRect(Color(0xFF012169), Offset.Zero, Size(w, h))
                drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.22f)
                drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.22f)
                drawLine(Color(0xFFC8102E), Offset(0f, 0f), Offset(w, h), strokeWidth = h * 0.09f)
                drawLine(Color(0xFFC8102E), Offset(w, 0f), Offset(0f, h), strokeWidth = h * 0.09f)
                drawRect(Color.White, Offset(w * 0.4f, 0f), Size(w * 0.2f, h))
                drawRect(Color.White, Offset(0f, h * 0.35f), Size(w, h * 0.3f))
                drawRect(Color(0xFFC8102E), Offset(w * 0.44f, 0f), Size(w * 0.12f, h))
                drawRect(Color(0xFFC8102E), Offset(0f, h * 0.41f), Size(w, h * 0.18f))
            }
        }
    }
}
