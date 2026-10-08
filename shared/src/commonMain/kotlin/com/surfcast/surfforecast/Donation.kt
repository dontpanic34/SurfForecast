package com.surfcast.surfforecast

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.sp

/** Lien de don (PayPal.Me). */
const val DONATION_URL = "https://www.paypal.me/othmanmiara"

/** Bouton « Offrir une wax » (en-tête des Paramètres) : ouvre directement PayPal. */
@Composable
fun DonationButton() {
    val uri = LocalUriHandler.current
    TextButton(onClick = { uri.openUri(DONATION_URL) }) {
        Text("🤙 Offrir une wax", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
}
