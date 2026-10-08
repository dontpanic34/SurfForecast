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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lien de don (PayPal.Me). Vide = la section « Offrir une wax » reste cachée. */
const val DONATION_URL = "https://www.paypal.me/othmanmiara"

/** Bouton « Offrir une wax avec PayPal », au-dessus de « Enregistrer et fermer » dans les Paramètres. */
@Composable
fun DonationSection() {
    if (DONATION_URL.isBlank()) return
    val colors = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Surf Log est gratuit et sans pub. Si l'appli te sert, tu peux m'offrir une wax : le montant est libre.",
            fontSize = 10.5.sp,
            color = colors.onBackground.copy(alpha = 0.75f)
        )
        Button(
            onClick = { uri.openUri(DONATION_URL) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant)
        ) { Text("🤙 Offrir une wax avec PayPal", fontSize = 11.5.sp, color = colors.onBackground) }
    }
}
