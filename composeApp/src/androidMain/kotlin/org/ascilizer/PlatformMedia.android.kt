package org.ascilizer

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.BitmapFactory

private object AndroidAppContextHolder {
    @Volatile
    var context: android.content.Context? = null
}

internal fun registerAndroidAppContext(context: android.content.Context) {
    AndroidAppContextHolder.context = context.applicationContext
}

@Composable
actual fun rememberImagePicker(onImagePicked: (SelectedImage?) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onImagePicked)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        callback.value(uri?.toSelectedImage(context))
    }

    return remember(launcher) {
        object : ImagePickerLauncher {
            override fun launch() {
                launcher.launch("image/*")
            }
        }
    }
}

actual suspend fun saveResultImage(
    bytes: ByteArray,
    suggestedFileName: String,
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val context = requireNotNull(AndroidAppContextHolder.context) {
            "Android context is not ready."
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, suggestedFileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Ascilizer")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
                "Unable to create a download entry."
            }
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Couldn't write the downloaded image.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .resolve("Ascilizer")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            File(downloadsDir, suggestedFileName).outputStream().use { it.write(bytes) }
        }

        "Saved to Downloads/Ascilizer."
    }
}

@Composable
actual fun PlatformPreviewImage(
    bytes: ByteArray,
    remoteUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
) {
    val bitmap = remember(bytes) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

private fun Uri.toSelectedImage(context: android.content.Context): SelectedImage? {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(this)?.use { it.readBytes() } ?: return null
    val mimeType = resolver.getType(this) ?: "image/jpeg"
    val fileName = queryDisplayName(resolver, this) ?: "image.jpg"
    return SelectedImage(
        bytes = bytes,
        mimeType = mimeType,
        fileName = fileName,
    )
}

private fun queryDisplayName(
    resolver: android.content.ContentResolver,
    uri: Uri,
): String? {
    val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
    val cursor: Cursor = resolver.query(uri, projection, null, null, null) ?: return null
    cursor.use {
        val index = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        if (index >= 0 && it.moveToFirst()) {
            return it.getString(index)
        }
    }
    return null
}
