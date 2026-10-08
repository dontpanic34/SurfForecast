package com.surfcast.surfforecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lien de don (PayPal.Me, Ko-fi…). Vide = le bouton « Soutenir » reste caché. */
const val DONATION_URL = "https://www.paypal.me/othmanmiara"

/** Petit bouton « Soutenir » (en-tête des Paramètres) qui ouvre la fenêtre de don : PayPal ou QR Wero. */
@Composable
fun DonationButton(weroQr: @Composable () -> Unit) {
    if (DONATION_URL.isBlank()) return
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text("🤙 Soutenir", fontSize = 12.sp, color = colors.primary)
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
                        "Surf Log est gratuit et sans pub. Si l'appli te sert, tu peux me soutenir : le montant est libre.",
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
                    ) { Text(if (showWero) "Masquer le QR Wero" else "Avec Wero (QR code)", fontSize = 12.sp, color = colors.onSurface) }
                    if (showWero) {
                        Text("Scanne ce QR code depuis l'appli Wero (ou ta banque).", fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.75f))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(Modifier.width(190.dp)) { weroQr() }
                        }
                    }
                }
            }
        )
    }
}
