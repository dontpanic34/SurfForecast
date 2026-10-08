package com.surfcast.surfforecast

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.sp

/** Lien de don (PayPal.Me). */
const val DONATION_URL = "https://www.paypal.me/othmanmiara"

/** Bouton « Offrir une wax » (bas du menu des Paramètres) : ouvre directement PayPal. */
@Composable
fun DonationButton(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = { uri.openUri(DONATION_URL) },
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
    ) { Text("🤙 Offrir une wax", fontSize = 13.sp, color = colors.onPrimary) }
}
