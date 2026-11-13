package com.surfcast.surfforecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

private fun dec(v: Double): String = formatDecimal(v, 1).replace('.', ',')

/**
 * Petite ligne grise sous les heures de l'encart Houle : la réponse pour l'heure choisie (« Au bord séries à la poitrine › »).
 * Un toucher ouvre la feuille « Ce que ça donne au bord ».
 */
@Composable
fun SwellAtShoreLine(spotName: String, hour: HourlyUiModel, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val result = SwellAtShoreCalc.compute(hour.waveHeight, hour.wavePeriod, ShoreSettings.typeFor(spotName))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 2.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.onSurface.copy(alpha = 0.6f))) { append("Au bord  ") }
                withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) {
                    append(result.headline.replaceFirstChar { it.lowercase() })
                }
            },
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f)
        )
        Chevron(colors.onSurface.copy(alpha = 0.5f))
    }
}

/** Feuille « Ce que ça donne au bord » : pour l'heure choisie dans l'encart Houle, ou une houle réglée à la main. */
@Composable
fun SwellAtShoreSheet(spotName: String, hour: HourlyUiModel, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var manual by remember { mutableStateOf(false) }
    var manualHs by remember { mutableDoubleStateOf((hour.waveHeight * 10).roundToInt() / 10.0) }
    var manualT by remember { mutableDoubleStateOf(hour.wavePeriod.roundToInt().toDouble()) }
    var type by remember { mutableStateOf(ShoreSettings.typeFor(spotName)) }
    var typeMenu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    val hs = if (manual) manualHs else hour.waveHeight
    val t = if (manual) manualT else hour.wavePeriod
    val r = SwellAtShoreCalc.compute(hs, t, type)
    val hourLabel = "${hour.rawTime.hour} h"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = colors.background
            ) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(colors.onSurface.copy(alpha = 0.2f))
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ce que ça donne au bord", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                            Text(
                                if (manual) "$spotName · houle réglée à la main" else "$spotName · $hourLabel",
                                fontSize = 12.5.sp, color = colors.onBackground.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(onClick = { showInfo = !showInfo }) {
                            Icon(
                                SurfIcons.Info, contentDescription = "Comment c'est calculé",
                                tint = if (showInfo) colors.primary else colors.onBackground.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = onDismiss) { Icon(SurfIcons.Close, contentDescription = "Fermer", tint = colors.onBackground) }
                    }
                    if (showInfo) ShoreInfo()

                    // Heure choisie ou houle réglée à la main.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("Heure choisie", selected = !manual, modifier = Modifier.weight(1f)) { manual = false }
                        Pill("Régler à la main", selected = manual, modifier = Modifier.weight(1f)) { manual = true }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(
                            if (manual) "À la main · ${dec(hs)} m · ${t.roundToInt()} s" else "$hourLabel · ${dec(hs)} m · ${t.roundToInt()} s",
                            selected = false
                        ) {}
                        Spacer(Modifier.weight(1f))
                        Box {
                            Pill("${type.label} ▾", selected = false) { typeMenu = true }
                            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                                ShoreType.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label, fontWeight = if (option == type) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            type = option
                                            ShoreSettings.setType(spotName, option)
                                            typeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (manual) {
                        Surface(shape = RoundedCornerShape(12.dp), color = colors.surface) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text("Hauteur  ${dec(manualHs)} m", fontSize = 12.5.sp, color = colors.onSurface)
                                Slider(
                                    value = manualHs.toFloat(), valueRange = 0.2f..4f, steps = 37,
                                    onValueChange = { manualHs = (it * 10).roundToInt() / 10.0 }
                                )
                                Text("Période  ${manualT.roundToInt()} s", fontSize = 12.5.sp, color = colors.onSurface)
                                Slider(
                                    value = manualT.toFloat(), valueRange = 5f..20f, steps = 14,
                                    onValueChange = { manualT = it.roundToInt().toDouble() }
                                )
                            }
                        }
                    }

                    // La réponse : « Séries à la poitrine », une ligne de chiffres, une phrase courte.
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(r.headline, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.primary, lineHeight = 28.sp)
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) {
                                        append("${dec(r.low)}–${dec(r.high)} m, max ${dec(r.biggest)} m")
                                    }
                                    withStyle(SpanStyle(color = colors.onSurface.copy(alpha = 0.6f))) {
                                        append(" · ${r.shape.label} · ${r.swellName}")
                                    }
                                },
                                fontSize = 12.5.sp
                            )
                            Text(r.feel, fontSize = 13.sp, color = colors.onSurface)
                        }
                    }

                    // Six petites tuiles.
                    val tiles = listOf(
                        Triple("${r.wavesPerSet.first}–${r.wavesPerSet.last}", "", "vagues par série"),
                        Triple("${r.minutesBetweenSets.first}–${r.minutesBetweenSets.last}", "min", "entre les séries"),
                        Triple("${r.setDurationSeconds.first}–${r.setDurationSeconds.last}", "s", "par série"),
                        Triple(dec(r.lull), "m", "entre deux séries"),
                        Triple(dec(r.biggest), "m", "les plus grosses"),
                        Triple("${r.energyKj}", "kJ", "par vague")
                    )
                    tiles.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (value, unit, label) ->
                                Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.weight(1f)) {
                                    Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            buildAnnotatedString {
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.onSurface)) { append(value) }
                                                if (unit.isNotEmpty()) withStyle(SpanStyle(fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.6f))) { append(" $unit") }
                                            }
                                        )
                                        Text(label, fontSize = 10.5.sp, color = colors.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center, lineHeight = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "Taille calée sur Montalivet (1,3 m à 14 s = 1,6 m au bord). Repères de surfeur : 1,5 m taille d'homme, 2 m overhead.",
                        fontSize = 11.sp, color = colors.onBackground.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

/** Le « i » : comment la taille au bord et les séries sont calculées, en mots simples. */
@Composable
private fun ShoreInfo() {
    val colors = MaterialTheme.colorScheme
    val lines = listOf(
        "Taille au bord" to "la formule de Komar et Gaughan (hauteur et période au large), calée sur ce qu'on voit à Montalivet : 1,3 m à 14 s au large donne 1,6 m au bord. Le type de spot l'ajuste : bancs marqués + 16 %, récif ou pointe + 25 %.",
        "Repères" to "on surfe fléchi : 1,5 m au bord, c'est taille d'homme ; 2 m, overhead.",
        "Forme de la vague" to "selon la pente du fond et le rapport entre la hauteur et la période (nombre d'Iribarren) : glissante, creuse ou gonflée.",
        "Séries" to "plus la période est longue, plus les séries sont longues et espacées (ordres de grandeur d'observation). Durée d'une série = nombre de vagues × période.",
        "Énergie" to "énergie d'une vague par mètre de crête, la même que les kJ de l'encart Houle."
    )
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Comment c'est calculé", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            lines.forEach { (title, text) ->
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$title : ") }
                        append(text)
                    },
                    fontSize = 12.5.sp, color = colors.onSurface, lineHeight = 17.sp
                )
            }
            Text(
                "Ce sont des estimations : le banc de sable, la marée et le vent changent beaucoup ce qu'on voit au bord.",
                fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.6f), lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        color = if (selected) colors.primaryContainer else colors.surface
    ) {
        Text(
            text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun Chevron(color: Color) {
    Canvas(Modifier.size(12.dp)) {
        val p = Path().apply {
            moveTo(size.width * 0.35f, size.height * 0.15f)
            lineTo(size.width * 0.7f, size.height * 0.5f)
            lineTo(size.width * 0.35f, size.height * 0.85f)
        }
        drawPath(p, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
