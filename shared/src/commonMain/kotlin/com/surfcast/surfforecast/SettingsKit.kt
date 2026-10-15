package com.surfcast.surfforecast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Le style des réglages : listes groupées, séparateurs fins, icônes au trait de même épaisseur, valeur à droite et
 * aide courte sous le réglage (maquette `reglages-pro`). Tous les écrans de réglages et les ⚙ des encarts l'utilisent.
 */
object SettingsIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()

    val User = icon("user", "M8.5 8a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0", "M5 20c.6-3.6 3.4-5.5 7-5.5s6.4 1.9 7 5.5")
    val Board = icon("board", "M12 2.5c3 3.2 4 7.5 4 11.5s-1 6-4 7.5c-3-1.5-4-3.5-4-7.5s1-8.3 4-11.5Z", "M12 5v14")
    val Tune = icon("tune", "M4 7h9M17 7h3M4 17h3M11 17h9", "M13 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M7 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    val Layers = icon("layers", "m12 3 9 5-9 5-9-5 9-5Z", "m3 13 9 5 9-5")
    val Ruler = icon("ruler", "M4.5 8h15a1.5 1.5 0 0 1 1.5 1.5v5a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 14.5v-5A1.5 1.5 0 0 1 4.5 8Z", "M7 8v3M11 8v4M15 8v3M19 8v4")
    val Chart = icon("chart", "M4 20V5M4 20h16", "m7 15 4-5 3 3 5-6")
    val Widget = icon(
        "widget",
        "M5.5 4h4A1.5 1.5 0 0 1 11 5.5v4A1.5 1.5 0 0 1 9.5 11h-4A1.5 1.5 0 0 1 4 9.5v-4A1.5 1.5 0 0 1 5.5 4Z",
        "M14.5 4h4A1.5 1.5 0 0 1 20 5.5v4a1.5 1.5 0 0 1-1.5 1.5h-4A1.5 1.5 0 0 1 13 9.5v-4A1.5 1.5 0 0 1 14.5 4Z",
        "M5.5 13h4A1.5 1.5 0 0 1 11 14.5v4A1.5 1.5 0 0 1 9.5 20h-4A1.5 1.5 0 0 1 4 18.5v-4A1.5 1.5 0 0 1 5.5 13Z",
        "M14.5 13h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-4a1.5 1.5 0 0 1 1.5-1.5Z"
    )
    val Save = icon("save", "M12 4v11M7.5 10.5 12 15l4.5-4.5M5 19h14")
    val Book = icon("book", "M5 4.5h10a3 3 0 0 1 3 3V20H8a3 3 0 0 1-3-3V4.5Z", "M5 17a3 3 0 0 1 3-3h10")
    val Compass = icon("compass", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0", "m15.5 8.5-2 5-5 2 2-5 5-2Z")
    val Globe = icon("globe", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0", "M3.5 12h17", "M12 3.5c2.5 2.6 3.5 5.5 3.5 8.5S14.5 17.9 12 20.5C9.5 17.9 8.5 15 8.5 12S9.5 6.1 12 3.5Z")
    val Heart = icon("heart", "M12 20s-7.5-4.6-7.5-10A4.2 4.2 0 0 1 12 7.6 4.2 4.2 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10Z")
    val Search = icon("search", "M5 11a6 6 0 1 0 12 0a6 6 0 1 0 -12 0", "m16 16 4 4")
    val Chevron = icon("chevron", "m9 6 6 6-6 6")
    val Back = icon("back", "m15 6-6 6 6 6")
    val Close = icon("close", "M6 6l12 12M18 6 6 18")
    val Wind = icon("wind", "M3 9h10a2.5 2.5 0 1 0-2.5-2.5M3 14h14a2.5 2.5 0 1 1-2.5 2.5M3 19h7")
    val Wave = icon("wave", "M2.5 9c2.5-3 4.5-3 7 0s4.5 3 7 0 3.5-2 5 0M2.5 15c2.5-3 4.5-3 7 0s4.5 3 7 0 3.5-2 5 0")
    val Cloud = icon("cloud", "M7 18a4 4 0 0 1-.5-8A5.5 5.5 0 0 1 17 8.5 4.8 4.8 0 0 1 17.5 18H7Z")
    val Gear = icon(
        "gear",
        "M19.4 13c.04-.32.07-.65.07-1s-.03-.68-.07-1l2.1-1.65c.2-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.3-.61-.22l-2.49 1c-.52-.4-1.08-.73-1.69-.98l-.38-2.65A.49.49 0 0 0 14 2h-4c-.25 0-.46.18-.49.42l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1a.5.5 0 0 0-.61.22l-2 3.46c-.13.22-.07.49.12.64L4.6 11c-.04.32-.07.65-.07 1s.03.68.07 1l-2.1 1.65c-.19.15-.24.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1c.52.4 1.08.73 1.69.98l.38 2.65c.03.24.24.42.49.42h4c.25 0 .46-.18.49-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1c.23.09.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64L19.4 13Z",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0"
    )
    val TextSize = icon("text", "M4 18 9 6l5 12M5.8 14h6.4M15 18l3-7 3 7M16 16h4")
}

@Composable
fun SettingsIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Barre du haut d'un écran de réglages : retour (si [onBack]) à gauche, titre au centre, fermer à droite. */
@Composable
fun SettingsTopBar(title: String, onBack: (() -> Unit)?, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(44.dp).clickable(enabled = onBack != null) { onBack?.invoke() }, contentAlignment = Alignment.Center) {
            if (onBack != null) SettingsIcon(SettingsIcons.Back, colors.primary)
        }
        Text(tr(title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, modifier = Modifier.weight(1f), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(modifier = Modifier.size(44.dp).clickable { onClose() }, contentAlignment = Alignment.Center) {
            SettingsIcon(SettingsIcons.Close, colors.onSurfaceVariant)
        }
    }
}

/** Un groupe de réglages : un titre en petites capitales au-dessus d'une liste arrondie à fin contour. */
@Composable
fun SettingsSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(tr(title).uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
                .border(BorderStroke(1.dp, colors.outlineVariant), RoundedCornerShape(14.dp)),
            content = content
        )
    }
}

@Composable
fun SettingsDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Une rangée : icône, titre, aide en dessous, valeur à droite et chevron si elle ouvre un écran. [top] = trait au-dessus (faux pour la première). */
@Composable
fun SettingsRow(
    title: String, subtitle: String? = null, icon: ImageVector? = null, value: String? = null, badge: String? = null,
    chevron: Boolean = true, top: Boolean = true, accentValue: Boolean = false, onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    if (top) SettingsDivider()
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            SettingsIcon(icon)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(tr(title), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            if (subtitle != null) Text(tr(subtitle), fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant)
        }
        if (badge != null) {
            Text(tr(badge), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer,
                modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        if (value != null) {
            if (accentValue) Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            else Text(value, fontSize = 13.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        }
        if (onClick != null && chevron) {
            Spacer(Modifier.width(4.dp))
            SettingsIcon(SettingsIcons.Chevron, colors.onSurfaceVariant, 18.dp)
        }
    }
}

/** Un champ à plusieurs lignes dans un groupe : titre et valeur en haut, contrôle dessous, aide en bas. */
@Composable
fun SettingsField(title: String, value: String? = null, hint: String? = null, top: Boolean = true, control: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (top) SettingsDivider()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(tr(title), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, modifier = Modifier.weight(1f))
            if (value != null) Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
        }
        control()
        if (hint != null) Text(tr(hint), fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant)
    }
}

/** La taille du texte des choix : plus petite dans les lignes de réglage compactes (tiroir de l'engrenage). */
val LocalSegmentFontSize = androidx.compose.runtime.compositionLocalOf { 13.sp }

/** Choix entre quelques options sur une seule ligne, l'option choisie cernée. */
@Composable
fun SettingsSegments(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val fontSize = LocalSegmentFontSize.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.background)
            .border(BorderStroke(1.dp, colors.outlineVariant), RoundedCornerShape(10.dp)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 36.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (on) colors.primaryContainer else Color.Transparent)
                    .then(if (on) Modifier.border(BorderStroke(1.dp, colors.primary), RoundedCornerShape(8.dp)) else Modifier)
                    .clickable { onSelect(key) }.padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr(label), fontSize = fontSize, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    color = if (on) colors.onPrimaryContainer else colors.onSurfaceVariant)
            }
        }
    }
}

/** Un curseur avec son titre, sa valeur lue en clair et une aide. */
@Composable
fun SettingsSliderField(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, hint: String? = null, top: Boolean = true, onChange: (Float) -> Unit) {
    SettingsField(title, valueText, hint, top) {
        val cs = MaterialTheme.colorScheme
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = cs.primary, activeTrackColor = cs.primary, inactiveTrackColor = cs.outlineVariant, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
        )
    }
}

@Composable
fun SettingsSwitchRow(title: String, subtitle: String? = null, checked: Boolean, top: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (top) SettingsDivider()
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tr(title), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            if (subtitle != null) Text(tr(subtitle), fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Un lien d'aide qui ouvre une leçon ou une explication. */
@Composable
fun SettingsHelpLink(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
            .border(BorderStroke(1.dp, colors.outlineVariant), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(SettingsIcons.Book)
        Spacer(Modifier.width(12.dp))
        Text(tr(text), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.primary, modifier = Modifier.weight(1f))
        SettingsIcon(SettingsIcons.Chevron, colors.primary, 18.dp)
    }
}

/** Un petit texte d'explication sous un groupe. */
@Composable
fun SettingsNote(text: String) {
    Text(tr(text), fontSize = 12.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

/** Un cadre en pointillés pour l'aperçu en direct sous un réglage. */
@Composable
fun SettingsPreviewBox(label: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.background)
            .border(BorderStroke(1.dp, colors.outlineVariant), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(tr(label).uppercase(), fontSize = 11.sp, letterSpacing = 0.8.sp, color = colors.onSurfaceVariant)
        content()
    }
}

/** Une réponse à choisir (un seul choix : rond ; plusieurs : case) avec son titre et une aide. */
@Composable
fun SettingsChoiceRow(title: String, hint: String? = null, selected: Boolean, multi: Boolean = false, top: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (top) SettingsDivider()
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
            .background(if (selected) colors.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tr(title), fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = colors.onSurface)
            if (!hint.isNullOrBlank()) Text(tr(hint), fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        val shape = if (multi) RoundedCornerShape(6.dp) else RoundedCornerShape(50)
        Box(
            modifier = Modifier.size(22.dp).clip(shape)
                .background(if (selected) colors.primary else Color.Transparent)
                .border(BorderStroke(2.dp, if (selected) colors.primary else colors.outline), shape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(modifier = Modifier.size(if (multi) 10.dp else 8.dp).clip(if (multi) RoundedCornerShape(2.dp) else RoundedCornerShape(50)).background(colors.onPrimary))
        }
    }
}

/** Un bouton : plein pour l'action principale, cerné pour une action secondaire. */
@Composable
fun SettingsButton(text: String, primary: Boolean = true, enabled: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg = if (!enabled) colors.onSurface.copy(alpha = 0.08f) else if (primary) colors.primary else Color.Transparent
    val fg = if (!enabled) colors.onSurface.copy(alpha = 0.38f) else if (primary) colors.onPrimary else colors.primary
    Box(
        modifier = modifier.defaultMinSize(minHeight = 46.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .then(if (!primary) Modifier.border(BorderStroke(1.dp, if (enabled) colors.primary else colors.outlineVariant), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(tr(text), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg) }
}

/** Une barre de progression fine. */
@Composable
fun SettingsProgress(fraction: Float) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.outlineVariant)) {
        Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(4.dp).background(colors.primary))
    }
}

/** Un titre de bloc en petites capitales, hors liste. */
@Composable
fun SettingsLabel(text: String) {
    Text(tr(text).uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}
