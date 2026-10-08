package com.surfcast.surfforecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lien de don (PayPal.Me). */
const val DONATION_URL = "https://www.paypal.me/othmanmiara"

/** Numéro lié au compte Wero (affiché à côté du QR code). */
const val WERO_PHONE = "06 65 35 83 24"

/** Bouton « Offrir une wax » (en-tête des Paramètres) : ouvre le choix PayPal ou Wero (numéro / QR code). */
@Composable
fun DonationButton(weroQr: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text("🤙 Offrir une wax", fontSize = 12.sp, color = colors.primary)
    }
    if (open) {
        val uri = LocalUriHandler.current
        var showWero by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Fermer") } },
            title = { Text("Offrir une wax 🤙", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Surf Log est gratuit et sans pub. Si l'appli te sert, tu peux m'offrir une wax : le montant est libre.",
                        fontSize = 12.sp,
                        color = colors.onSurface.copy(alpha = 0.8f)
                    )
                    Button(
                        onClick = { uri.openUri(DONATION_URL) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) { Text("Avec PayPal", fontSize = 12.sp, color = colors.onPrimary) }
                    Button(
                        onClick = { showWero = !showWero },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
                    ) { Text(if (showWero) "Masquer Wero" else "Avec Wero", fontSize = 12.sp, color = colors.onSurface) }
                    if (showWero) {
                        Text(
                            "Dans ton appli Wero, envoie de l'argent à ce numéro :",
                            fontSize = 11.sp,
                            color = colors.onSurface.copy(alpha = 0.75f)
                        )
                        SelectionContainer {
                            Text(WERO_PHONE, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        }
                        Text(
                            "Ou scanne ce QR code depuis un autre écran :",
                            fontSize = 11.sp,
                            color = colors.onSurface.copy(alpha = 0.75f)
                        )
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(Modifier.width(170.dp)) { weroQr() }
                        }
                    }
                }
            }
        )
    }
}
