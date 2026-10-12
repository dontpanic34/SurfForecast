@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Questionnaire de niveau : une question à la fois, comme un prof de surf (maquette docs/maquettes/profil-questions.html).

/** Une question : [id] sert aussi de clé de stockage, [multi] = plusieurs réponses possibles. */
private class Step(
    val id: String, val section: String, val title: String, val sub: String = "", val options: List<String>,
    val optionHints: List<String> = emptyList(), val multi: Boolean = false
)

private const val SEC_PREFS = "Tes préférences"
private const val SEC_LEVEL = "Ton niveau"
private const val SEC_FORM = "Ta forme"
private const val MULTI_SUB = "Plusieurs réponses possibles."

private val STEPS: List<Step> = listOf(
    Step("tol1", SEC_PREFS, "Quand les conditions sont moyennes (vent, mer hachée, vague molle)…", "",
        listOf("J'y vais quand même, motivé : je reviendrai si ça s'améliore", "J'y vais si ça reste correct", "J'attends que ce soit bon")),
    Step("tol2", SEC_PREFS, "Quand c'est plus gros que d'habitude…", "",
        listOf("J'y vais, c'est l'occasion de progresser", "J'hésite : j'y vais si c'est accessible et que je suis en forme", "Je reste au sec")),
    Step("tol3", SEC_PREFS, "Ça souffle onshore, ou c'est dur physiquement…", "",
        listOf("Je me débrouille, il y a toujours des bouts à prendre", "Je tente un peu, puis je vois", "Je préfère rester au sec")),
    Step("what", SEC_LEVEL, "Tu surfes…", "", listOf("La mousse", "La reforme", "Le large")),
    Step("barre", SEC_LEVEL, "Jusqu'à quelle hauteur de vague tu passes la barre ?", options = listOf(
        "Je ne passe pas encore la barre", "Vagues jusqu'aux genoux (environ 0,5 m)", "Vagues jusqu'à la taille (environ 0,8 m)", "Vagues jusqu'à la poitrine (environ 1,2 m)", "Vagues jusqu'à la tête (environ 1,5 m)", "Vagues plus hautes que la tête (environ 2 m)")),
    Step("tk", SEC_LEVEL, "Définis ton take-off", options = listOf(
        "En un seul temps, tout le temps", "En un seul temps, souvent", "Parfois en un seul temps, ou en plusieurs temps")),
    Step("want", SEC_LEVEL, "Tu veux passer au large ?", options = listOf("Oui, bientôt", "Pas pour l'instant")),
    Step("peak", SEC_LEVEL, "Où tu te places dans l'eau ?", "",
        listOf("J'ai du mal à localiser le pic, je me fie aux gens", "Je repère le pic tout seul, j'y vais si c'est accessible", "Je sais où est le pic, j'essaie d'être le mieux placé")),
    Step("goal", SEC_LEVEL, "Sur la vague, tu cherches surtout à…", MULTI_SUB, listOf(
        "Réussir tes take-offs", "Enchaîner quelques manœuvres pour suivre la vague, sans prendre trop de risques",
        "Devenir de plus en plus radical, critique et engagé", "Affiner ton surf : le style, la précision, chaque détail"), multi = true),
    Step("man", SEC_LEVEL, "Tes manœuvres", "", listOf(
        "Je n'en suis pas là : je cherche surtout à comprendre et suivre le rythme de la vague",
        "Le mouvement est là, mais la réalisation est brouillonne : pas très propre, ça passe parfois",
        "Le mouvement est bon, il reste des détails à régler pour replaquer à chaque fois",
        "Je replaque à chaque fois : j'affine le style, la puissance, et je vise des endroits plus critiques")),
    Step("feel", SEC_FORM, "Quand tu passes la barre, tu te sens…", "", listOf(
        "Prêt à surfer", "Impressionné : je m'éloigne du pic, je me repose, j'analyse", "Je passe, mais en galère", "Je passe si je suis au bon endroit, au bon moment"),
        ),
    Step("train", SEC_FORM, "Tu t'entraînes en dehors du surf ?", "", listOf(
        "Non", "De temps en temps (course, natation, yoga…)", "Régulièrement, pour le surf (cardio, renfo, apnée…)")),
    Step("where", SEC_FORM, "Où tu surfes ?", MULTI_SUB, listOf(
        "Chez moi, avec du monde : ça me rassure", "Autour de chez moi : je choisis le meilleur spot, j'évite le monde",
        "Je me déplace pour suivre les conditions, si ça vaut le coup", "Je pars à l'étranger chercher les vagues (surf trips)"), multi = true)
)

/** Définition et « ce que tu cherches », une par tranche (Débutant, Débutant +, Intermédiaire, …, Expert). */
private val LEVEL_DEFINITIONS = listOf(
    "Tu surfes la mousse (ou les reformes). Ton take-off est à travailler ou se fait en plusieurs temps. Tu ne passes pas la barre, ou seulement quand c'est tout petit." to
        "Te lever, et garder la mousse le plus longtemps possible.",
    "Tu surfes la mousse (ou les reformes) et ton take-off est sûr, en un seul temps. Tu as envie d'aller vers la vague : tu regardes les petits jours, avec du monde autour pour te rassurer." to
        "Passer de la mousse à la vague.",
    "Tu passes la barre quand c'est accessible et tu surfes l'épaule. Tu te lèves souvent sur la vague. Tu te places en fonction des autres : le monde te rassure. Le pic t'impressionne." to
        "Réussir tes take-offs sur la vague.",
    "Tu surfes l'épaule avec des take-offs de plus en plus réguliers. Tu commences à te placer tout seul vers le pic, même si tu préfères encore qu'il y ait du monde." to
        "Te lever à chaque vague, et te placer seul.",
    "Tu es autonome : tu localises une vague même s'il n'y a personne. Ton take-off est solide, tu passes la barre sans souci et la rame suit." to
        "Suivre la vague le plus longtemps possible.",
    "Tu lis la vague avant qu'elle ne change : tes replacements deviennent naturels et tu surfes de plus en plus près de la partie qui pousse. Tes manœuvres restent simples." to
        "Surfer plus près de la partie qui pousse.",
    "Tu cherches la taille où tu peux faire de vraies manœuvres et l'endroit critique de la vague. Tu sais faire des manœuvres de pro, mais pas de façon régulière : il te reste une marge de progression avant de les considérer acquises." to
        "Exprimer tes manœuvres, t'améliorer, être plus critique.",
    "Ton mouvement est bon sur tes manœuvres de pro ; il te reste quelques détails à régler pour replaquer à chaque fois." to
        "Rendre tes manœuvres régulières, avec puissance et style.",
    "Toutes les manœuvres sont acquises : tu les perfectionnes. Pas de limite : le gros, le creux, le tube. Tu surfes aussi quand c'est pourri : tu trouves le bon créneau et tu tiens le courant à la rame." to
        "Perfectionner des manœuvres que tu maîtrises déjà."
)

/** Ce que l'écran de profil a besoin pour le questionnaire : les réponses enregistrées, le résultat et la sauvegarde. */
class LevelQuizHost(
    val answers: LevelAnswers?, val shift: Int, val tolerance: List<Int>, val result: LevelResult?,
    val onSave: (LevelAnswers, Int, List<Int>) -> Unit
)

/** Réponses en cours : un index (ou plusieurs) par question, selon [Step.id]. */
private fun toAnswers(s: Map<String, List<Int>>): LevelAnswers = LevelAnswers(
    what = s["what"]?.firstOrNull() ?: 2, barre = s["barre"]?.firstOrNull(), takeOff = s["tk"]?.firstOrNull(),
    peak = s["peak"]?.firstOrNull(), goals = s["goal"].orEmpty(), maneuvers = s["man"]?.firstOrNull(),
    wantLarge = s["want"]?.firstOrNull(), feel = s["feel"]?.firstOrNull(), training = s["train"]?.firstOrNull(), where = s["where"].orEmpty()
)

private fun fromAnswers(a: LevelAnswers, tol: List<Int>): Map<String, List<Int>> {
    val m = LinkedHashMap<String, List<Int>>()
    tol.forEachIndexed { i, v -> m["tol${i + 1}"] = listOf(v) }
    m["what"] = listOf(a.what)
    a.barre?.let { m["barre"] = listOf(it) }
    a.takeOff?.let { m["tk"] = listOf(it) }
    a.wantLarge?.let { m["want"] = listOf(it) }
    a.peak?.let { m["peak"] = listOf(it) }
    if (a.goals.isNotEmpty()) m["goal"] = a.goals
    a.maneuvers?.let { m["man"] = listOf(it) }
    a.feel?.let { m["feel"] = listOf(it) }
    a.training?.let { m["train"] = listOf(it) }
    if (a.where.isNotEmpty()) m["where"] = a.where
    return m
}

private fun activeSteps(s: Map<String, List<Int>>): List<Step> {
    val a = toAnswers(s)
    return STEPS.filter {
        when (it.id) {
            "barre", "peak", "goal", "feel" -> a.what == 2
            "want" -> a.what < 2
            "man" -> showManeuvers(a)
            else -> true
        }
    }
}

/** Barre de niveau qui se remplit comme de l'expérience, avec le nom du niveau (le chiffre n'est jamais affiché). */
@Composable
fun LevelXpBar(score: Int, modifier: Modifier = Modifier, height: Int = 10) {
    val colors = MaterialTheme.colorScheme
    val fill by animateFloatAsState(targetValue = (score / 100f).coerceIn(0f, 1f), label = "xp")
    Box(modifier = modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50)).background(colors.onSurface.copy(alpha = 0.10f))) {
        Box(
            modifier = Modifier.fillMaxWidth(fill.coerceAtLeast(0.02f)).fillMaxHeight().clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(colors.primary, Color(0xFF6FD3A5))))
        )
    }
}

/** Le niveau actuel en un coup d'œil : nom + barre d'XP. */
@Composable
fun LevelCard(result: LevelResult, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(result.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        LevelXpBar(result.score)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LEVEL_NAMES.forEach { Text(it, fontSize = 9.5.sp, color = colors.onSurfaceVariant, maxLines = 1) }
        }
    }
}

/**
 * Le questionnaire en écran : questions, barre d'XP provisoire en direct, puis le résultat avec « Pour passer à… ».
 * [initial] = réponses déjà données (pour refaire le questionnaire), [onDone] reçoit les réponses, le décalage choisi
 * (une marche en dessous / au-dessus) et les trois réponses de tolérance.
 */
@Composable
fun LevelQuestionnaire(
    initial: LevelAnswers?,
    initialShift: Int,
    initialTolerance: List<Int>,
    onDone: (LevelAnswers, Int, List<Int>) -> Unit,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val state = remember { mutableStateMapOf<String, List<Int>>().also { m -> if (initial != null) m.putAll(fromAnswers(initial, initialTolerance)) } }
    var index by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(initialShift) }
    val steps = activeSteps(state)
    val answers = toAnswers(state)

    if (finished) {
        val result = computeLevel(answers, shift)
        val level = LEVEL_DEFINITIONS[result.bandIndex]
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ton niveau", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, letterSpacing = 0.8.sp)
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceVariant).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LevelCard(result)
                Text(level.first, fontSize = 13.sp, color = colors.onBackground)
                Text("Sur la vague, tu cherches : " + level.second, fontSize = 12.5.sp, color = colors.onSurfaceVariant)
            }
            result.nextName?.let { next ->
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Pour passer à « $next »", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    result.advice?.let { Text(it, fontSize = 12.5.sp, color = colors.onBackground) }
                    // Un « + » veut dire qu'un seul point te retient : on le nomme.
                    if (result.plus && result.holdBack.size == 1) {
                        Text("Aujourd'hui, ce qui te retient : " + result.holdBack.first() + ".", fontSize = 11.5.sp, color = colors.onSurfaceVariant)
                    }
                }
            } ?: Text("Tu es au sommet de l'échelle : tu perfectionnes.", fontSize = 12.5.sp, color = colors.onBackground)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { shift -= 1 }, enabled = result.bandIndex > 0, modifier = Modifier.weight(1f)) { Text("− Une marche", fontSize = 12.sp) }
                OutlinedButton(onClick = { shift += 1 }, enabled = result.bandIndex < 8, modifier = Modifier.weight(1f)) { Text("+ Une marche", fontSize = 12.sp) }
            }
            Button(
                onClick = { onDone(answers, shift, listOf("tol1", "tol2", "tol3").mapNotNull { state[it]?.firstOrNull() }) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("C'est mon niveau", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { finished = false; index = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Refaire le questionnaire") }
        }
        return
    }

    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val selected = state[step.id].orEmpty()
    // Barre d'XP provisoire : seulement quand deux réponses du niveau comptent déjà.
    val known = if (answers.what == 2) listOfNotNull(answers.barre, answers.takeOff, answers.peak, answers.goals.firstOrNull()).size
    else if (answers.takeOff != null) 2 else 0
    fun advance() { if (index >= steps.lastIndex) finished = true else index += 1 }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "${step.section.uppercase()} · ${index + 1} SUR ${steps.size}",
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, letterSpacing = 0.8.sp
        )
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceVariant)) {
            Box(modifier = Modifier.fillMaxWidth((index + 1f) / steps.size).fillMaxHeight().background(colors.primary))
        }
        if (known >= 2) {
            val live = computeLevel(answers)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) { LevelXpBar(live.score, height = 8) }
                Text(live.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Text("provisoire", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
            }
        }
        Text(step.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        if (step.sub.isNotBlank()) Text(step.sub, fontSize = 13.sp, color = colors.onSurfaceVariant)
        step.options.forEachIndexed { i, label ->
            val on = i in selected
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (on) colors.primary else colors.surfaceVariant)
                    .clickable {
                        if (step.multi) {
                            state[step.id] = if (on) selected - i else selected + i
                        } else {
                            state[step.id] = listOf(i)
                            advance()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (on) colors.onPrimary else colors.onBackground)
                step.optionHints.getOrNull(i)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 11.5.sp, color = if (on) colors.onPrimary else colors.onSurfaceVariant)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (index > 0) index -= 1 else onClose() }) { Text(if (index > 0) "‹ Précédent" else "Fermer") }
            if (step.multi) {
                Button(onClick = { advance() }, enabled = selected.isNotEmpty()) { Text(if (index >= steps.lastIndex) "Voir mon profil ›" else "Suivant ›") }
            }
        }
    }
}

/**
 * Comparer les niveaux : une échelle de cinq marches (Débutant à Expert) et, dessous, le détail de la marche touchée.
 * Ce qui est acquis (ta marche et celles d'en dessous) est écrit en gras et plus grand ; ce qui reste à venir en petit.
 */
@Composable
fun LevelLadder(result: LevelResult, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val mine = result.bandIndex / 2
    var selected by remember(mine) { mutableStateOf(mine) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Comparer les niveaux", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LEVEL_NAMES.forEachIndexed { i, name ->
                val acquired = i <= mine
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (i == selected) colors.primary.copy(alpha = 0.22f) else colors.surfaceVariant)
                        .clickable { selected = i }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(if (i == mine) 14.dp else 10.dp).clip(RoundedCornerShape(50))
                        .background(if (acquired) colors.primary else colors.onSurface.copy(alpha = 0.2f)))
                    Text(name, fontSize = 9.5.sp, fontWeight = if (i == mine) FontWeight.Bold else FontWeight.Normal, color = colors.onBackground, maxLines = 1)
                }
            }
        }
        val step = LADDER[selected]
        val acquired = selected <= mine
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(LEVEL_NAMES[selected] + if (selected == mine) " · ton niveau" else if (acquired) " · acquis" else " · à venir",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            // Ce qui définit la marche : en gras et plus grand quand c'est acquis, petit quand ça reste à venir.
            step.have.forEach {
                Text(it, fontSize = if (acquired) 13.5.sp else 11.5.sp, fontWeight = if (acquired) FontWeight.Bold else FontWeight.Normal,
                    color = if (acquired) colors.onBackground else colors.onSurfaceVariant)
            }
            Text("À travailler : " + step.work.joinToString(" · "), fontSize = 11.5.sp, color = colors.onSurfaceVariant)
            step.next?.let { Text("Pour la marche d'après : $it", fontSize = 11.5.sp, color = colors.onSurfaceVariant) }
        }
    }
}

/** Une marche de l'échelle : ce qui la définit, ce qu'on y travaille, ce qu'il faut pour la suivante (tiré des réponses du questionnaire). */
private class LadderStep(val have: List<String>, val work: List<String>, val next: String?)

private val LADDER = listOf(
    LadderStep(
        listOf("Tu surfes la mousse, ou tu restes sur les reformes.", "Tu te lèves, ou tu apprends à le faire."),
        listOf("take-off en un seul temps", "passer la barre quand c'est petit"),
        "Surfer l'épaule d'une vague sans mousse."
    ),
    LadderStep(
        listOf("Tu passes la barre quand c'est accessible.", "Tu te lèves souvent sur la vague et tu surfes l'épaule."),
        listOf("te placer seul vers le pic", "réussir chaque take-off"),
        "Être autonome : localiser une vague même sans personne autour."
    ),
    LadderStep(
        listOf("Tu es autonome et tu localises la vague seul.", "Ton take-off est solide, tu passes la barre sans souci."),
        listOf("replacements pour rester en rythme", "surfer plus près de la partie qui pousse"),
        "Faire des manœuvres de pro, même sans régularité."
    ),
    LadderStep(
        listOf("Tu lis la vague : tes replacements sont naturels.", "Tes manœuvres simples sont maîtrisées."),
        listOf("manœuvres de pro régulières", "chercher l'endroit critique de la vague"),
        "Toutes les manœuvres acquises, avec puissance et style."
    ),
    LadderStep(
        listOf("Toutes les manœuvres sont acquises.", "Le gros, le creux, le tube : pas de limite.", "Tu surfes aussi quand c'est pourri."),
        listOf("perfectionner ce que tu maîtrises"),
        null
    )
)
