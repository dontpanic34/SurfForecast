package com.surfcast.surfforecast

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/*
 * Photos de session sur iOS : choisies avec le sélecteur système (PHPicker, pas besoin
 * d'autorisation d'accès à toute la photothèque), converties en JPEG et copiées dans
 * Documents/session_media. On stocke un chemin RELATIF ("session_media/xxx.jpg") car le
 * dossier de l'app change de chemin à chaque mise à jour sur iOS.
 */

private const val MEDIA_DIR = "session_media"

@OptIn(ExperimentalForeignApi::class)
private fun documentsPath(): String? = NSFileManager.defaultManager.URLForDirectory(
    directory = NSDocumentDirectory,
    inDomain = NSUserDomainMask,
    appropriateForURL = null,
    create = true,
    error = null
)?.path

private fun absoluteMediaPath(relative: String): String? = documentsPath()?.let { "$it/$relative" }

private class PhotoPickerDelegate(private val onPicked: (String) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val relative = data?.let { saveAsJpeg(it) } ?: return@loadDataRepresentationForTypeIdentifier
            dispatch_async(dispatch_get_main_queue()) { onPicked(relative) }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun saveAsJpeg(data: NSData): String? {
    // UIImage lit aussi le HEIC des iPhone, que le moteur d'affichage ne sait pas décoder.
    val image = UIImage(data = data)
    val jpeg = UIImageJPEGRepresentation(image, 0.85) ?: return null
    val documents = documentsPath() ?: return null
    NSFileManager.defaultManager.createDirectoryAtPath(
        "$documents/$MEDIA_DIR",
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )
    val relative = "$MEDIA_DIR/${NSUUID().UUIDString}.jpg"
    return if (jpeg.writeToFile("$documents/$relative", atomically = true)) relative else null
}

private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

@Composable
actual fun rememberMediaPicker(onPicked: (String) -> Unit): (() -> Unit)? {
    // Le délégué doit rester référencé tant que le sélecteur est ouvert (référence faible côté UIKit).
    val delegate = remember { PhotoPickerDelegate(onPicked) }
    return {
        val config = PHPickerConfiguration().apply {
            selectionLimit = 1
            filter = PHPickerFilter.imagesFilter
        }
        val picker = PHPickerViewController(configuration = config)
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun loadBitmap(relative: String): ImageBitmap? {
    val path = absoluteMediaPath(relative) ?: return null
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isEmpty()) return null
    bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}

@Composable
actual fun SessionMediaView(mediaUri: String) {
    val bitmap = remember(mediaUri) { loadBitmap(mediaUri) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Photo de session",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        // Ex : média ajouté sur Android (URI content://) ou fichier supprimé.
        Text(
            text = "📷 Média joint",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
