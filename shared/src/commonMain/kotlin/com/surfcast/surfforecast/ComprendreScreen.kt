package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

/**
 * La section « Comprendre » : trois chapitres, onze leçons courtes et trois petits outils à manipuler.
 * Le texte vient de docs/COMPRENDRE_HOULE.md. N'importe quel écran peut l'ouvrir sur une leçon avec [ComprendreState.open].
 */
object ComprendreState {
    /** null = fermé ; "" = l'accueil ; sinon l'identifiant d'une leçon. */
    var request by mutableStateOf<String?>(null)
        private set

    fun open(lessonId: String = "") { request = lessonId }
    fun close() { request = null }

    /** La leçon qui explique le mieux chaque encart (clé du ⚙). */
    fun lessonForCard(cardKey: String): String = when (cardKey) {
        "swell" -> "periode"
        "week" -> "arrivee"
        "day" -> "modeles"
        "wind" -> "carte"
        else -> ""
    }
}

internal class Lesson(val id: String, val title: String, val body: String, val takeaway: String, val inApp: String? = null)
internal class Chapter(val title: String, val subtitle: String, val lessons: List<Lesson>)

internal val COMPRENDRE_CHAPTERS: List<Chapter> = listOf(
    Chapter("La houle", "Période, séries, arrivée", listOf(
        Lesson("houle-vague", "Houle et vague, pas la même chose",
            "La **houle** voyage : elle transporte de l'énergie à travers l'océan. La **vague** est ce qui arrive quand cette énergie se dépense, en touchant le fond près de la côte.",
            "On prévoit la houle, on surfe la vague."),
        Lesson("periode", "La période, la vraie puissance",
            "Deux houles de même hauteur ne se valent pas : la période compte **au carré**. Une houle de 1,5 m à 8 s libère environ 283 kJ ; une houle de 1 m à 14 s environ 385 kJ, donc plus, alors qu'elle est plus petite. Règle de poche : chaque seconde en plus, environ +20 % d'énergie.",
            "Une petite houle longue peut valoir mieux qu'une grosse houle courte.",
            "Les kJ affichés tiennent déjà compte de la période."),
        Lesson("hauteur", "Hauteur significative et séries",
            "La hauteur annoncée est la moyenne du **tiers des plus grosses vagues**. Les séries montent plus haut, environ un tiers de plus : 0,9 m annoncé veut dire des séries vers 1,2 m. Des houles de périodes presque identiques s'additionnent : parfois elles se renforcent, parfois elles s'annulent. C'est ce qui fait les séries.",
            "Le chiffre est une moyenne, pas un maximum."),
        Lesson("arrivee", "Quand arrive la houle ?",
            "Une houle voyage à environ **0,75 × sa période** en mètres par seconde. Si la dépression est un peu plus petite, la période baisse : la houle arrive plus tard et plus petite.",
            "Au-delà de trois jours, l'heure d'arrivée est une plage, pas un horaire.",
            "Les prévisions lointaines peuvent glisser d'une demi-journée : à partir de J+4, les étoiles sont une tendance."),
        Lesson("phase", "Les vagues vont plus vite que la houle",
            "Dans un train de houle, chaque vague avance **deux fois plus vite** que le train : elle remonte vers l'avant puis disparaît, comme la chenille d'un tank. (Vrai au large, pas près de la côte.)",
            "La houle arrive d'un bloc, ses vagues défilent dedans.")
    )),
    Chapter("La vague et le spot", "Fond, bancs de sable, énergie", listOf(
        Lesson("vague-fond", "Quand la houle devient vague",
            "Près de la côte, le fond freine la houle, l'eau s'empile et finit par tomber : elle gonfle sans casser, s'effondre ou plonge (le tube). Deux choses décident : la **période** et la **pente du fond**. Une vague casse dans une profondeur d'environ 1,3 fois sa hauteur. Le vent de terre, un courant d'embouchure ou de baïne aident au tube.",
            "Le même ciel donne des vagues très différentes selon le fond du spot."),
        Lesson("bancs", "Bancs de sable et baïnes",
            "Sur un fond de sable, la marée qui descend cherche une sortie et creuse un sillon : la baïne. Elle s'approfondit à chaque marée, puis s'élargit jusqu'à ce qu'une autre se forme à côté. Le **banc** compte : une marche nette donne un pic bien formé. Un banc vit quelques semaines.",
            "Un spot de sable change d'une semaine à l'autre.",
            "Ton journal de sessions est le meilleur moyen de le suivre."),
        Lesson("energie", "Lire l'énergie (les kJ)",
            "Les modèles décrivent la houle en plein de morceaux. L'**énergie** les additionne en un seul chiffre : elle compare deux houles qui font « 1,8 m à 10 s » sur le papier. L'**étalement directionnel** dit si la houle est rangée (petit) ou en chantier (grand). Débutant : la taille. Intermédiaire : ajoute la période. Expert : l'énergie. Un spot commence par exemple à marcher vers 250 kJ et sature vers 600 kJ.",
            "Un chiffre d'énergie vaut mieux que hauteur et période pris séparément.")
    )),
    Chapter("Les prévisions", "Modèles, cartes, bouées", listOf(
        Lesson("modeles", "Pourquoi les prévisions se trompent",
            "Un modèle découpe la mer en carrés. Il ne voit que ce qui est **5 à 7 fois plus grand** que son carré : à 25 km il oublie les îles ; à 5 km il voit encore mal la côte. Les modèles **fins** sont meilleurs près du trait de côte ; les modèles **globaux** meilleurs pour les grandes houles venues de loin.",
            "Côte : un modèle fin. Grosse houle qui arrive : un modèle global.",
            "Pour la houle, l'appli fait le consensus de trois modèles (MF-WAM, ECMWF, GFS) : les fins pèsent plus aujourd'hui et demain, les trois à égalité ensuite."),
        Lesson("carte", "Lire une carte",
            "1. Repère les **dépressions** : le vent tourne autour, d'autant plus fort que les isobares sont serrées. 2. Regarde la **carte de la période** plutôt que celle des hauteurs. 3. Puis le vent sur ton secteur. 4. Seulement ensuite, ton site de prévision pour le spot.",
            "Houle d'abord, vent ensuite, détail du spot en dernier."),
        Lesson("bouee", "Vérifier avec la bouée",
            "Une **bouée** mesure la houle pour de vrai. Si elle voit 1,3 m et la prévision 0,8 m, la prévision est trop basse sur ce secteur. Son spectre montre si la houle est rangée ou mélangée.",
            "La bouée est la vérité du moment, la prévision est une hypothèse.",
            "Sous la carte de houle, la bouée de référence de ton spot (si elle est à moins de 120 km) donne la dernière mesure.")
    ))
)

private fun richText(text: String, boldColor: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, p ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) { append(p) } else append(p)
    }
}

private sealed class CView {
    object Home : CView()
    class Chapter(val index: Int) : CView()
    class LessonView(val chapter: Int, val index: Int) : CView()
}

private fun locate(lessonId: String): CView? {
    COMPRENDRE_CHAPTERS.forEachIndexed { c, ch -> ch.lessons.forEachIndexed { l, le -> if (le.id == lessonId) return CView.LessonView(c, l) } }
    return null
}

@Composable
fun ComprendreDialog(startLesson: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var view by remember(startLesson) { mutableStateOf<CView>(locate(startLesson) ?: CView.Home) }
    Dialog(onDismissRequest = onDismiss) {
      // La fenêtre épouse son contenu (jusqu'à 90 % de l'écran), au lieu d'être toujours haute.
      BoxWithConstraints {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.9f).clip(RoundedCornerShape(14.dp)),
            color = colors.background
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val current = view
                    when (current) {
                        is CView.Home -> Text("Comprendre", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                        is CView.Chapter -> TextButton(onClick = { view = CView.Home }) { Text("‹ Comprendre", fontSize = 13.sp, color = colors.primary) }
                        is CView.LessonView -> TextButton(onClick = { view = CView.Chapter(current.chapter) }) {
                            Text("‹ " + COMPRENDRE_CHAPTERS[current.chapter].title, fontSize = 13.sp, color = colors.primary)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(imageVector = SurfIcons.Close, contentDescription = "Fermer", tint = colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (val v = view) {
                        is CView.Home -> {
                            Text("Houle, vagues et prévisions, en leçons courtes.", fontSize = 13.sp, color = colors.onSurfaceVariant)
                            COMPRENDRE_CHAPTERS.forEachIndexed { i, ch ->
                                ComprendreRow(ch.title, "${ch.subtitle} · ${ch.lessons.size} leçons") { view = CView.Chapter(i) }
                            }
                            Text("La météo : bientôt.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                        is CView.Chapter -> {
                            Text(COMPRENDRE_CHAPTERS[v.index].title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                            COMPRENDRE_CHAPTERS[v.index].lessons.forEachIndexed { j, le ->
                                ComprendreRow(le.title, null) { view = CView.LessonView(v.index, j) }
                            }
                        }
                        is CView.LessonView -> {
                            val chapter = COMPRENDRE_CHAPTERS[v.chapter]
                            val le = chapter.lessons[v.index]
                            Text(le.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                            Text(richText(le.body, colors.onBackground), fontSize = 14.sp, lineHeight = 20.sp, color = colors.onBackground)
                            when (le.id) {
                                "periode" -> EnergyTool()
                                "arrivee" -> ArrivalTool()
                                "vague-fond" -> BreakingTool()
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.primary.copy(alpha = 0.12f)).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("À retenir", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                                Text(le.takeaway, fontSize = 14.sp, color = colors.onBackground)
                            }
                            le.inApp?.let {
                                Column(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text("Dans Surf Log", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                                    Text(it, fontSize = 13.sp, color = colors.onSurfaceVariant)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                if (v.index > 0) TextButton(onClick = { view = CView.LessonView(v.chapter, v.index - 1) }) { Text("‹ Précédente", color = colors.primary) } else Spacer(Modifier)
                                if (v.index < chapter.lessons.size - 1) TextButton(onClick = { view = CView.LessonView(v.chapter, v.index + 1) }) { Text("Suivante ›", color = colors.primary) } else Spacer(Modifier)
                            }
                        }
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun ComprendreRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            subtitle?.let { Text(it, fontSize = 12.sp, color = colors.onSurfaceVariant) }
        }
        Text("›", fontSize = 18.sp, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun ToolBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { content() }
}

@Composable
private fun ToolSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, text: String, onChange: (Float) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = colors.onSurface)
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
    }
    Slider(
        value = value, onValueChange = onChange, valueRange = range,
        colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary, inactiveTrackColor = colors.outlineVariant, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
    )
}

private fun comma(v: Double, digits: Int = 1) = formatDecimal(v, digits).replace('.', ',')

@Composable
private fun EnergyTool() {
    val colors = MaterialTheme.colorScheme
    var ah by remember { mutableStateOf(1.5f) }
    var ap by remember { mutableStateOf(8f) }
    var bh by remember { mutableStateOf(1.0f) }
    var bp by remember { mutableStateOf(14f) }
    val ae = calculateWaveEnergyReal(ah.toDouble(), ap.roundToInt().toDouble())
    val be = calculateWaveEnergyReal(bh.toDouble(), bp.roundToInt().toDouble())
    ToolBox {
        ToolSlider("Houle A : hauteur", ah, 0.3f..3f, comma(ah.toDouble()) + " m") { ah = (it * 10).roundToInt() / 10f }
        ToolSlider("Houle A : période", ap, 5f..20f, ap.roundToInt().toString() + " s") { ap = it }
        ToolSlider("Houle B : hauteur", bh, 0.3f..3f, comma(bh.toDouble()) + " m") { bh = (it * 10).roundToInt() / 10f }
        ToolSlider("Houle B : période", bp, 5f..20f, bp.roundToInt().toString() + " s") { bp = it }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("A : $ae kJ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text("B : $be kJ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        }
        val diff = if (ae > 0) ((be.toDouble() / ae - 1.0) * 100).roundToInt() else 0
        Text(
            if (kotlin.math.abs(diff) < 3) "À peu près la même énergie." else "La houle ${if (diff > 0) "B" else "A"} a ${kotlin.math.abs(if (diff > 0) diff else ((ae.toDouble() / be - 1.0) * 100).roundToInt())} % d'énergie de plus que l'autre.",
            fontSize = 12.sp, color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun ArrivalTool() {
    val colors = MaterialTheme.colorScheme
    var km by remember { mutableStateOf(1800f) }
    var period by remember { mutableStateOf(14f) }
    val speed = 0.75 * period
    val hours = (km * 1000.0 / speed / 3600.0).roundToInt()
    val hoursShort = (km * 1000.0 / (0.75 * 10.0) / 3600.0).roundToInt()
    ToolBox {
        ToolSlider("Distance de la tempête", km, 500f..4000f, "${km.roundToInt()} km") { km = (it / 100).roundToInt() * 100f }
        ToolSlider("Période de la houle", period, 8f..20f, "${period.roundToInt()} s") { period = it }
        Text("Arrive dans environ $hours h", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text(
            "Vitesse : ${(speed * 3.6).roundToInt()} km/h. Pour comparer, une houle de 10 s mettrait $hoursShort h" +
                if (hoursShort > hours) " : ${hoursShort - hours} h de plus." else ".",
            fontSize = 12.sp, color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun BreakingTool() {
    val colors = MaterialTheme.colorScheme
    var h by remember { mutableStateOf(1.5f) }
    ToolBox {
        ToolSlider("Hauteur de la vague", h, 0.3f..4f, comma(h.toDouble()) + " m") { h = (it * 10).roundToInt() / 10f }
        Text("Casse vers ${comma(h * 1.3, 2)} m de profondeur", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text("Elle casse quand la profondeur vaut environ 1,3 fois sa hauteur.", fontSize = 12.sp, color = colors.onSurfaceVariant)
    }
}
