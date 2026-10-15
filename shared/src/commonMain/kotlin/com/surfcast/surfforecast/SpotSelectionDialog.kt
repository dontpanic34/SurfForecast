@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.surfcast.surfforecast.ui.theme.AppColors

/** Les trois places de favoris : au-delà, il faut en remplacer un. */
private const val FAVORITE_SLOTS = 3

/**
 * Le choix du spot : la recherche en premier, les régions de tous les pays sur une seule ligne qui défile, un compteur de favoris
 * et une étoile par spot. Un tap sur la ligne ouvre le spot ; l'étoile l'ajoute aux favoris (ou le retire). À la première
 * ouverture ([mandatory]), c'est le même écran, sans bouton de fermeture : il faut choisir un spot.
 */
@Composable
fun SpotSelectionDialog(
    currentFavorites: List<String?>,
    onDismiss: () -> Unit,
    onSpotSelected: (String) -> Unit,
    onAssignToSlot: (String, Int) -> Unit,
    onRemoveFromSlot: (Int) -> Unit,
    onOpenLiveCam: (String) -> Unit,
    mandatory: Boolean = false,
    onChosen: ((String) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val allRegions = SurfDatabase.countries.flatMap { it.regions.flatMap { r -> r.subRegions } }
    // Un pays d'abord (France, Espagne, Portugal, Maroc), puis ses régions.
    var selectedCountry by remember { mutableStateOf(SurfDatabase.countries.firstOrNull()?.name) }
    val countryRegions = SurfDatabase.countries.firstOrNull { it.name == selectedCountry }?.regions?.flatMap { it.subRegions } ?: emptyList()
    var selectedRegion by remember { mutableStateOf(countryRegions.firstOrNull()?.name) }
    // Le spot dont on choisit la place de favori (menu ouvert sous son étoile).
    var menuFor by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    val favoriteCount = currentFavorites.take(FAVORITE_SLOTS).count { it != null }

    val filteredSpots = if (searchQuery.isNotBlank()) {
        SurfDatabase.getAllSpots().filter { it.name.contains(searchQuery, ignoreCase = true) }
    } else {
        countryRegions.firstOrNull { it.name == selectedRegion }?.spots ?: emptyList()
    }

    Dialog(
        onDismissRequest = { if (!mandatory) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !mandatory, dismissOnClickOutside = !mandatory)
    ) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).clip(RoundedCornerShape(14.dp)), color = colors.background) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (mandatory) "Où tu surfes ?" else "Choisir un spot",
                        fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = colors.onBackground
                    )
                    if (!mandatory) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                            Icon(imageVector = SurfIcons.Close, contentDescription = "Fermer", tint = colors.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher un spot...", fontSize = 13.sp, color = colors.onSurfaceVariant) },
                    leadingIcon = { Icon(SurfIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f),
                        focusedTextColor = colors.onBackground,
                        unfocusedTextColor = colors.onBackground,
                        cursorColor = colors.primary
                    )
                )

                if (searchQuery.isBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    // Les pays, puis les régions du pays choisi, chacun sur une ligne qui défile.
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SurfDatabase.countries.forEach { country ->
                            val on = selectedCountry == country.name
                            Text(
                                text = tr(country.name), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                                color = if (on) colors.onPrimary else colors.onSurfaceVariant,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.primary else colors.surfaceVariant)
                                    .clickable {
                                        if (!on) {
                                            selectedCountry = country.name
                                            selectedRegion = country.regions.flatMap { it.subRegions }.firstOrNull()?.name
                                        }
                                    }.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        countryRegions.forEach { region ->
                            val on = selectedRegion == region.name
                            Text(
                                text = tr(region.name), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                color = if (on) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) colors.primaryContainer else colors.surfaceVariant)
                                    .clickable { selectedRegion = region.name }.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (!mandatory) {
                    Text(
                        "Favoris $favoriteCount sur $FAVORITE_SLOTS · touche ★ pour choisir la place ou retirer",
                        fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                HorizontalDivider(color = colors.onSurfaceVariant.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredSpots) { spot ->
                        val slot = currentFavorites.take(FAVORITE_SLOTS).indexOf(spot.name)
                        val isFavorite = slot >= 0
                        Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)), color = colors.surfaceVariant) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        if (mandatory) onChosen?.invoke(spot.name) else onSpotSelected(spot.name)
                                        onDismiss()
                                    }
                                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = spot.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                                if (!mandatory) {
                                    Box {
                                        Text(
                                            text = if (isFavorite) "★" else "☆", fontSize = 24.sp,
                                            color = if (isFavorite) AppColors.WindMid else colors.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                                                if (isFavorite) onRemoveFromSlot(slot) else menuFor = spot.name
                                            }.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                        // Le menu des places : « Favori 1 · Hossegor », « Favori 2 · place libre »… ; choisir une place occupée la remplace.
                                        DropdownMenu(expanded = menuFor == spot.name, onDismissRequest = { menuFor = null }) {
                                            Text(tr("Mettre en favori"), fontSize = 11.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                                            for (i in 0 until FAVORITE_SLOTS) {
                                                val occupant = currentFavorites.getOrNull(i)
                                                DropdownMenuItem(
                                                    text = { Text(tr("Favori") + " " + (i + 1) + " · " + (occupant ?: tr("place libre")), fontSize = 14.sp, fontWeight = if (occupant == null) FontWeight.Bold else FontWeight.Normal) },
                                                    onClick = { onAssignToSlot(spot.name, i); menuFor = null }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filteredSpots.isEmpty()) {
                        item {
                            Text("Aucun spot trouvé.", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }
}
