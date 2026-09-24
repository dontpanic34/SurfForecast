@file:Suppress("SpellCheckingInspection")
package com.surfcast.surfforecast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.surfcast.surfforecast.ui.theme.AppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpotSelectionDialog(
    currentFavorites: List<String?>,
    onDismiss: () -> Unit,
    onSpotSelected: (String) -> Unit,
    onAssignToSlot: (String, Int) -> Unit,
    onRemoveFromSlot: (Int) -> Unit,
    onOpenLiveCam: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(SurfDatabase.countries.first().name) }
    var selectedRegion by remember { mutableStateOf<String?>(null) }
    var targetSlotForAssignment by remember { mutableStateOf<Int?>(null) }
    val colors = MaterialTheme.colorScheme

    val currentCountry = SurfDatabase.countries.firstOrNull { it.name == selectedCountry }
    val subRegionsForCountry = currentCountry?.regions?.flatMap { it.subRegions } ?: emptyList()

    // Si la région sélectionnée n'appartient pas (ou plus) au pays courant, on repart sur la première dispo
    LaunchedEffect(selectedCountry) {
        if (subRegionsForCountry.none { it.name == selectedRegion }) {
            selectedRegion = subRegionsForCountry.firstOrNull()?.name
        }
    }

    val filteredSpots = if (searchQuery.isNotBlank()) {
        SurfDatabase.getAllSpots().filter { it.name.contains(searchQuery, ignoreCase = true) }
    } else {
        subRegionsForCountry.firstOrNull { it.name == selectedRegion }?.spots ?: emptyList()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(14.dp)),
            color = colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sélection du Spot",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = colors.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher un spot...", fontSize = 13.sp, color = colors.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
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
                    Spacer(modifier = Modifier.height(12.dp))

                    // Pays (dynamique depuis SurfDatabase)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SurfDatabase.countries.forEach { country ->
                            val isSelected = selectedCountry == country.name
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { selectedCountry = country.name },
                                color = if (isSelected) colors.primary else colors.surfaceVariant
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = country.name,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Régions (retour à la ligne automatique, tout visible sans scroll)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subRegionsForCountry.forEach { subRegion ->
                            val isSelected = selectedRegion == subRegion.name
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { selectedRegion = subRegion.name },
                                color = if (isSelected) colors.primary.copy(alpha = 0.2f) else colors.surfaceVariant
                            ) {
                                Text(
                                    text = subRegion.name,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                HorizontalDivider(color = colors.onSurfaceVariant.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(6.dp))

                // Liste spots
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredSpots) { spot ->
                        val isFavorite = currentFavorites.contains(spot.name)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            color = colors.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (targetSlotForAssignment != null) {
                                            onAssignToSlot(spot.name, targetSlotForAssignment!!)
                                            targetSlotForAssignment = null
                                        } else {
                                            onSpotSelected(spot.name)
                                        }
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = spot.name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onBackground
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val camAvailable = SurfWebcamHelper.hasCamera(spot.name)
                                    IconButton(
                                        onClick = {
                                            if (camAvailable) {
                                                onOpenLiveCam(spot.name)
                                                onDismiss()
                                            }
                                        },
                                        enabled = camAvailable,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        WebcamIcon(
                                            tint = if (camAvailable) AppColors.WindMid else colors.onSurfaceVariant.copy(alpha = 0.3f),
                                            size = 16.dp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))

                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Favori",
                                        tint = if (isFavorite) AppColors.WindMid else colors.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (filteredSpots.isEmpty()) {
                        item {
                            Text(
                                text = "Aucun spot trouvé.",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}