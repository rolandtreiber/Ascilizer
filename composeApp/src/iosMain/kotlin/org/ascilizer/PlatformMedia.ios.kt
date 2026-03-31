package org.ascilizer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

private object IosUiHost {
    var rootController: UIViewController? = null
    var imagePickerDelegate: NSObject? = null
    var documentPickerDelegate: NSObject? = null
}

internal fun registerIosRootController(controller: UIViewController) {
    IosUiHost.rootController = controller
}

@Composable
actual fun rememberImagePicker(onImagePicked: (SelectedImage?) -> Unit): ImagePickerLauncher {
    val callback = rememberUpdatedState(onImagePicked)
    return remember {
        object : ImagePickerLauncher {
            override fun launch() {
                val host = IosUiHost.rootController.topController() ?: return
                val delegate = ImageImportPickerDelegate { image ->
                    callback.value(image)
                }
                IosUiHost.imagePickerDelegate = delegate
                val picker = UIDocumentPickerViewController(
                    documentTypes = listOf("public.image"),
                    inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
                )
                picker.delegate = delegate
                host.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

actual suspend fun saveResultImage(
    bytes: ByteArray,
    suggestedFileName: String,
): Result<String> = suspendCancellableCoroutine { continuation ->
    val host = IosUiHost.rootController.topController()
    if (host == null) {
        continuation.resume(Result.failure(Exception("iOS view controller is not ready.")))
        return@suspendCancellableCoroutine
    }

    val sheet = UIAlertController.alertControllerWithTitle(
        title = "Save image",
        message = "Choose where to save the converted image.",
        preferredStyle = UIAlertControllerStyleActionSheet,
    )

    sheet.addAction(
        UIAlertAction.actionWithTitle(
            title = "Photos",
            style = UIAlertActionStyleDefault,
            handler = {
                continuation.resume(
                    Result.failure(
                        Exception("Saving to Photos is temporarily unavailable. Please use Downloads."),
                    ),
                )
            },
        ),
    )
    sheet.addAction(
        UIAlertAction.actionWithTitle(
            title = "Downloads",
            style = UIAlertActionStyleDefault,
            handler = {
                exportImageToFiles(bytes, suggestedFileName) { result ->
                    continuation.resume(result)
                }
            },
        ),
    )
    sheet.addAction(
        UIAlertAction.actionWithTitle(
            title = "Cancel",
            style = UIAlertActionStyleCancel,
            handler = {
                continuation.resume(Result.failure(Exception("Save cancelled.")))
            },
        ),
    )

    host.presentViewController(sheet, animated = true, completion = null)
}

@Composable
actual fun PlatformPreviewImage(
    bytes: ByteArray,
    remoteUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (bytes.isNotEmpty()) {
                "Preview unavailable on iOS yet.\nYou can still save the converted image."
            } else {
                "No preview available."
            },
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(24.dp),
        )
    }
}

private fun exportImageToFiles(
    bytes: ByteArray,
    suggestedFileName: String,
    onComplete: (Result<String>) -> Unit,
) {
    val tempUrl = NSURL.fileURLWithPath("${NSTemporaryDirectory()}$suggestedFileName")
    val data = bytes.toNSDataOrNull()
    if (data == null) {
        onComplete(Result.failure(Exception("Couldn't prepare the image bytes for export.")))
        return
    }
    if (!data.writeToURL(tempUrl, atomically = true)) {
        onComplete(Result.failure(Exception("Couldn't stage the image for export.")))
        return
    }

    val host = IosUiHost.rootController.topController()
    if (host == null) {
        onComplete(Result.failure(Exception("The Files exporter is unavailable.")))
        return
    }

    val delegate = DocumentPickerDelegate { success ->
        if (success) {
            onComplete(Result.success("Choose a folder in Files to finish saving."))
        } else {
            onComplete(Result.failure(Exception("File export cancelled.")))
        }
    }
    IosUiHost.documentPickerDelegate = delegate

    val picker = UIDocumentPickerViewController(
        forExportingURLs = listOf(tempUrl),
        asCopy = true,
    )
    picker.delegate = delegate
    host.presentViewController(picker, animated = true, completion = null)
}

private class ImageImportPickerDelegate(
    val onImagePicked: (SelectedImage?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onImagePicked(null)
        IosUiHost.imagePickerDelegate = null
    }

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        val data = url?.let { NSData.dataWithContentsOfURL(it) }
        val bytes = data?.toByteArray()
        onImagePicked(
            bytes?.let {
                SelectedImage(
                    bytes = it,
                    mimeType = "image/jpeg",
                    fileName = url?.lastPathComponent ?: "image.jpg",
                )
            },
        )
        IosUiHost.imagePickerDelegate = null
    }
}

private class DocumentPickerDelegate(
    val onComplete: (Boolean) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onComplete(false)
        IosUiHost.documentPickerDelegate = null
    }

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        onComplete(didPickDocumentsAtURLs.isNotEmpty())
        IosUiHost.documentPickerDelegate = null
    }
}

private fun UIViewController?.topController(): UIViewController? {
    var current = this
    while (current?.presentedViewController != null) {
        current = current.presentedViewController
    }
    return current
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun ByteArray.toNSDataOrNull(): NSData? =
    runCatching {
        if (isEmpty()) {
            NSData()
        } else {
            usePinned { pinned ->
                NSData.create(
                    bytes = pinned.addressOf(0),
                    length = size.toULong(),
                )
            }
        }
    }.getOrNull()

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, length)
    }
    return bytes
}
