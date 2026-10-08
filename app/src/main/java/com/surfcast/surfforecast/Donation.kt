package com.surfcast.surfforecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lien de don (PayPal.Me, Ko-fi…). Vide = la section « Soutenir » reste cachée. */
const val DONATION_URL = "https://www.paypal.me/othmanmiara"

@Composable
fun DonationSection(weroQr: @Composable () -> Unit) {
    if (DONATION_URL.isBlank()) return
    val colors = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Soutenir", fontSize = 11.5.sp, color = colors.onBackground.copy(alpha = 0.7f))
        Text(
            "Surf Log est gratuit et sans pub. Si l'appli te sert, tu peux m'offrir une wax : le montant est libre.",
            fontSize = 10.5.sp,
            color = colors.onBackground.copy(alpha = 0.75f)
        )
        Button(
            onClick = { uri.openUri(DONATION_URL) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) { Text("🤙 Offrir une wax avec PayPal", fontSize = 11.5.sp, color = colors.onPrimary) }
        var showWero by remember { mutableStateOf(false) }
        Button(
            onClick = { showWero = !showWero },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
        ) { Text(if (showWero) "Masquer le QR Wero" else "Payer avec Wero (QR code)", fontSize = 11.5.sp, color = colors.onBackground) }
        if (showWero) {
            Text(
                "Scanne ce QR code depuis l'appli Wero (ou ta banque).",
                fontSize = 10.5.sp,
                color = colors.onBackground.copy(alpha = 0.75f)
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.width(200.dp)) { weroQr() }
            }
        }
    }
}
