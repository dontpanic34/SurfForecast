package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Les six conditions de la grille « Quand tu les sors » : (clé, libellé). */
val GEAR_CONDITIONS = listOf("petit" to "Petit", "gros" to "Gros", "mou" to "Mou", "creux" to "Creux", "puissant" to "Puissant", "longues" to "Longues vagues")

/**
 * Ce que le surfeur dit de sa pratique et de son quiver : quelle planche pour quelles conditions (grille), depuis quand et
 * à quelle fréquence il surfe, et où afficher la planche du jour. Enregistré par le contrôleur ([persist]).
 */
object GearPrefs {
    /** Grille : « id=petit,mou;id=gros » (id de la planche). */
    var grid by mutableStateOf("")
        private set
    var since by mutableStateOf("")
        private set
    var frequency by mutableStateOf("")
        private set
    var season by mutableStateOf("")
        private set
    /** Où montrer la planche du jour : « header », « now », « both » ou « none ». */
    var boardDayMode by mutableStateOf("both")
        private set

    var persist: (String, String) -> Unit = { _, _ -> }

    fun load(grid: String, since: String, frequency: String, season: String, mode: String) {
        this.grid = grid; this.since = since; this.frequency = frequency; this.season = season
        boardDayMode = if (mode in listOf("header", "now", "both", "none")) mode else "both"
    }

    fun chooseSince(v: String) { since = v; persist("gear_since", v) }
    fun chooseFrequency(v: String) { frequency = v; persist("gear_frequency", v) }
    fun chooseSeason(v: String) { season = v; persist("gear_season", v) }
    fun chooseBoardDayMode(v: String) { boardDayMode = v; persist("gear_board_day", v) }

    private fun parse(): Map<Long, Set<String>> = grid.split(";").mapNotNull { part ->
        val kv = part.split("=")
        val id = kv.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        id to kv.getOrElse(1) { "" }.split(",").filter { it.isNotBlank() }.toSet()
    }.toMap()

    fun conditionsOf(boardId: Long): Set<String> = parse()[boardId].orEmpty()

    fun toggle(boardId: Long, condition: String) {
        val m = parse().toMutableMap()
        val cur = m[boardId].orEmpty()
        m[boardId] = if (condition in cur) cur - condition else cur + condition
        grid = m.entries.joinToString(";") { (id, set) -> "$id=" + set.joinToString(",") }
        persist("gear_grid", grid)
    }
}

/** Les conditions d'une heure, dans les mots de la grille. */
fun gearConditionsOf(hour: HourlyUiModel, beachFacing: Int?): Set<String> {
    val cat = windCategoryFor(hour.windDirectionStr, beachFacing)
    val wind = effectiveWindKmh(hour)
    val out = mutableSetOf<String>()
    if (hour.waveHeight < 0.8) out += "petit"
    if (hour.waveHeight >= 1.6) out += "gros"
    if (hour.wavePeriod < 8.0 || (cat == "onshore" && wind >= 10)) out += "mou"
    if (cat == "offshore" && hour.waveHeight >= 1.0) out += "creux"
    if (hour.energyKj >= 600) out += "puissant"
    if (hour.wavePeriod >= 12.0) out += "longues"
    return out
}

/** La planche du quiver qui colle le mieux aux conditions de [hour] d'après la grille du surfeur (null si rien ne colle). */
fun boardOfTheDay(hour: HourlyUiModel?, boards: List<QuiverBoard>, beachFacing: Int?): QuiverBoard? {
    if (hour == null || boards.isEmpty()) return null
    val now = gearConditionsOf(hour, beachFacing)
    return boards.map { b -> b to GearPrefs.conditionsOf(b.id).count { it in now } }
        .filter { it.second > 0 }.maxByOrNull { it.second }?.first
}

@Composable
private fun PillChoices(choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { c ->
            val on = c == selected
            Text(
                c, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) colors.onPrimary else colors.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.primary else colors.surface)
                    .clickable { onSelect(if (on) "" else c) }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** « Quand tu les sors » (la grille), « Ma pratique » et le réglage de la planche du jour. */
@Composable
fun GearUsageSection(boards: List<QuiverBoard>) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (boards.isNotEmpty()) {
            Text("Quand tu les sors", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Spacer(modifier = Modifier.weight(1.3f))
                    GEAR_CONDITIONS.forEach { (_, label) ->
                        Text(label.substringBefore(' '), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.onSurface.copy(alpha = 0.65f), maxLines = 1,
                            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                boards.forEach { b ->
                    val set = GearPrefs.conditionsOf(b.id)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.model, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, modifier = Modifier.weight(1.3f))
                        GEAR_CONDITIONS.forEach { (key, _) ->
                            val on = key in set
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                                        .background(if (on) colors.primary else colors.surfaceVariant)
                                        .clickable { GearPrefs.toggle(b.id, key) },
                                    contentAlignment = Alignment.Center
                                ) { if (on) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onPrimary) }
                            }
                        }
                    }
                }
            }
        }

        Text("Ma pratique", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Text("Je surfe depuis", fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.7f))
        PillChoices(listOf("moins d'1 an", "1 à 3 ans", "3 à 10 ans", "plus de 10 ans"), GearPrefs.since) { GearPrefs.chooseSince(it) }
        Text("Je surfe", fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.7f))
        PillChoices(listOf("quelques fois par an", "1 fois par mois", "1 fois par semaine", "plusieurs fois par semaine", "plusieurs fois par jour"), GearPrefs.frequency) { GearPrefs.chooseFrequency(it) }
        Text("Ma saison", fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.7f))
        PillChoices(listOf("toute l'année", "quand l'eau est bonne"), GearPrefs.season) { GearPrefs.chooseSeason(it) }

        Text("Planche du jour", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Text("Où afficher la planche conseillée d'après ta grille", fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.7f))
        val modes = listOf("header" to "En-tête", "now" to "Ligne Maintenant", "both" to "Les deux", "none" to "Aucune")
        PillChoices(modes.map { it.second }, modes.first { it.first == GearPrefs.boardDayMode }.second) { label ->
            GearPrefs.chooseBoardDayMode(modes.firstOrNull { it.second == label }?.first ?: GearPrefs.boardDayMode)
        }
    }
}
