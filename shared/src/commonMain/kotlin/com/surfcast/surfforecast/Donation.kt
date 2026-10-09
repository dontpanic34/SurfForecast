package com.surfcast.surfforecast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lien de don PayPal (PayPal.Me) : sans frais quand la personne envoie en « paiement entre proches ». */
const val DONATION_URL = "https://www.paypal.me/othmanmiara"

/** Lien de paiement Stripe (Payment Link) : page de paiement par carte, montant libre. */
const val CARD_URL = "https://buy.stripe.com/bJe5kDh1ugKN6zQ0rv6sw00"

/** Lien de paiement Lydia (vide = bouton masqué tant que le lien n'est pas fourni). */
const val LYDIA_URL = "https://pay.lydia.me/l?t=othmana103s"

private class PayOption(val badge: String, val badgeBg: Color, val badgeFg: Color, val title: String, val subtitle: String, val url: String)

private val payOptions = listOf(
    PayOption("CB", Color(0xFF29ABE0), Color.White, "Carte bancaire", "Visa, Mastercard, Apple Pay", CARD_URL),
    PayOption("PayPal", Color(0xFFFFC439), Color(0xFF003087), "PayPal", "Avec ton compte PayPal", DONATION_URL),
    PayOption("Lydia", Color(0xFF0F7BFF), Color.White, "Lydia", "Depuis l'appli Lydia", LYDIA_URL)
).filter { it.url.isNotBlank() }

/** Bouton « Soutenir » (bas du menu des Paramètres) : ouvre le choix du moyen de paiement. */
@Composable
fun DonationButton(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Button(
        onClick = { open = true },
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
    ) { Text("❤️ Soutenir Surf Log", fontSize = 13.sp, color = colors.onPrimary) }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = colors.background,
            title = { Text("❤️ Soutenir Surf Log", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.onBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Surf Log est gratuit, sans pub et sans compte. Si l'appli t'aide à trouver les bons créneaux, tu peux soutenir son " +
                            "développement, du montant que tu veux.",
                        fontSize = 13.sp, color = colors.onSurfaceVariant
                    )
                    payOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceVariant)
                                .clickable { uri.openUri(option.url) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(width = 46.dp, height = 30.dp).clip(RoundedCornerShape(6.dp)).background(option.badgeBg),
                                contentAlignment = Alignment.Center
                            ) { Text(option.badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = option.badgeFg) }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(option.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                                Text(option.subtitle, fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Chaque bouton ouvre la page du service de paiement : l'appli ne voit jamais de données bancaires.", fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Fermer") } }
        )
    }
}
