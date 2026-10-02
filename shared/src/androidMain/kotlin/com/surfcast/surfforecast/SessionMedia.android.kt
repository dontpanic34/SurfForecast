package com.surfcast.surfforecast

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
actual fun rememberMediaPicker(onPicked: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Certains fournisseurs ne supportent pas la permission persistante, on garde l'URI quand même.
            }
            onPicked(uri.toString())
        }
    }
    return { launcher.launch(arrayOf("image/*", "video/*")) }
}

@Composable
actual fun SessionMediaView(mediaUri: String) {
    val context = LocalContext.current
    // La permission de lecture persistante n'est pas garantie : getType() peut lever une
    // SecurityException après redémarrage du process.
    val isVideo = remember(mediaUri) {
        mediaUri.contains("video") || try {
            context.contentResolver.getType(Uri.parse(mediaUri))?.startsWith("video") == true
        } catch (_: SecurityException) {
            false
        }
    }
    if (isVideo) {
        OutlinedButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(mediaUri), "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                // Aucune appli pour lire la vidéo ou permission perdue : on ignore.
            }
        }) {
            Text("Lire la video", fontSize = 12.sp)
        }
    } else {
        AsyncImage(
            model = Uri.parse(mediaUri),
            contentDescription = "Photo de session",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}
