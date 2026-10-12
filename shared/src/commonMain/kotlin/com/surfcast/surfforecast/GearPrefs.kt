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

/**
 * La planche du quiver qui colle le mieux aux conditions de [hour] : d'abord d'après ta grille « Je la sors quand… » ; tant que
 * la grille est vide, d'après le type de planche (petit ou mou : mid-length, longboard, fish ; gros, creux ou puissant :
 * shortboard). Null sans planche ou sans aucune idée.
 */
fun boardOfTheDay(hour: HourlyUiModel?, boards: List<QuiverBoard>, beachFacing: Int?): QuiverBoard? {
    if (hour == null || boards.isEmpty()) return null
    val now = gearConditionsOf(hour, beachFacing)
    val byGrid = boards.map { b -> b to GearPrefs.conditionsOf(b.id).count { it in now } }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    if (byGrid != null) return byGrid
    if (boards.any { GearPrefs.conditionsOf(it.id).isNotEmpty() }) return null
    val soft = "petit" in now || "mou" in now || "longues" in now
    val strong = "gros" in now || "creux" in now || "puissant" in now
    fun fit(b: QuiverBoard): Int = when (b.family) {
        "longboard" -> if (soft) 3 else 0
        "mid-length" -> if (soft) 2 else 1
        "fish", "twin", "groveler" -> if (soft) 2 else 1
        "shortboard" -> if (strong) 3 else 1
        "mousse" -> 0
        else -> 1
    }
    return boards.maxByOrNull { fit(it) }?.takeIf { fit(it) > 0 }
}

@Composable
internal fun PillChoices(choices: List<String>, selected: String, onSelect: (String) -> Unit) {
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

/** Majoration du volume selon la fréquence de surf (proposition à valider) : 0 plusieurs fois par semaine ou plus. */
internal fun frequencyBoost(frequency: String): Double = when (frequency) {
    "1 fois par semaine" -> 0.05
    "1 fois par mois" -> 0.10
    "quelques fois par an" -> 0.15
    else -> 0.0
}

/** Les conditions où l'on sort cette planche, sur sa carte (Petit, Gros, Mou, Creux, Puissant, Longues vagues). */
@Composable
fun BoardConditionChips(boardId: Long) {
    val colors = MaterialTheme.colorScheme
    val set = GearPrefs.conditionsOf(boardId)
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text("Je la sors :", fontSize = 11.5.sp, color = colors.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
        GEAR_CONDITIONS.forEach { (key, label) ->
            val on = key in set
            Text(
                label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (on) colors.onPrimary else colors.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.primary else colors.surfaceVariant)
                    .clickable { GearPrefs.toggle(boardId, key) }.padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}

/** Où afficher la planche conseillée du jour : dans « Mes planches ». */
@Composable
fun BoardDayModeChooser() {
    val colors = MaterialTheme.colorScheme
    val modes = listOf("header" to "En-tête", "now" to "Ligne Actuellement", "both" to "Les deux", "none" to "Aucune")
    Text("Planche du jour", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
    Text("Où afficher la planche conseillée d'après tes conditions", fontSize = 12.sp, color = colors.onBackground.copy(alpha = 0.7f))
    PillChoices(modes.map { it.second }, modes.first { it.first == GearPrefs.boardDayMode }.second) { label ->
        GearPrefs.chooseBoardDayMode(modes.firstOrNull { it.second == label }?.first ?: GearPrefs.boardDayMode)
    }
}

/** « Mon expérience » : depuis quand, fréquence, saison. Seule la fréquence change le volume conseillé. */
@Composable
fun ExperienceSection() {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Je surfe depuis", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        PillChoices(listOf("moins d'1 an", "1 à 3 ans", "3 à 10 ans", "plus de 10 ans"), GearPrefs.since) { GearPrefs.chooseSince(it) }
        Text("Je surfe", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        PillChoices(listOf("quelques fois par an", "1 fois par mois", "1 fois par semaine", "plusieurs fois par semaine", "plusieurs fois par jour"), GearPrefs.frequency) { GearPrefs.chooseFrequency(it) }
        val boost = frequencyBoost(GearPrefs.frequency)
        if (boost > 0) Text("+${(boost * 100).toInt()} % de volume conseillé : on surfe moins, il faut un peu plus de flottaison.", fontSize = 12.sp, color = colors.primary)
        Text("Ma saison", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        PillChoices(listOf("toute l'année", "quand l'eau est bonne"), GearPrefs.season) { GearPrefs.chooseSeason(it) }
    }
}
