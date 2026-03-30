package org.ascilizer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
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
import platform.Foundation.writeToURL
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageView
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UINavigationControllerDelegateProtocol
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
                val delegate = ImagePickerDelegate { image ->
                    callback.value(image)
                }
                IosUiHost.imagePickerDelegate = delegate
                val picker = UIImagePickerController().apply {
                    sourceType = platform.UIKit.UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
                    allowsEditing = true
                    this.delegate = delegate
                }
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
                saveImageToPhotos(bytes) { result ->
                    continuation.resume(result)
                }
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
    contentDescription: String?,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            UIImageView().apply {
                clipsToBounds = true
            }
        },
        modifier = modifier,
        update = { view ->
            view.image = UIImage(data = bytes.toNSData())
        },
    )
}

actual fun isPreviewRenderable(bytes: ByteArray): Boolean =
    UIImage(data = bytes.toNSData()) != null

private fun saveImageToPhotos(
    bytes: ByteArray,
    onComplete: (Result<String>) -> Unit,
) {
    PHPhotoLibrary.requestAuthorization { status ->
        when (status) {
            PHAuthorizationStatusAuthorized,
            PHAuthorizationStatusLimited -> {
                val image = UIImage(data = bytes.toNSData())
                if (image == null) {
                    onComplete(Result.failure(Exception("Couldn't prepare the image for Photos.")))
                    return@requestAuthorization
                }
                PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                    changeBlock = {
                        PHAssetChangeRequest.creationRequestForAssetFromImage(image)
                    },
                    completionHandler = { success, error ->
                        if (success) {
                            onComplete(Result.success("Saved to Photos."))
                        } else {
                            onComplete(Result.failure(Exception(error?.localizedDescription ?: "Couldn't save to Photos.")))
                        }
                    },
                )
            }

            else -> onComplete(Result.failure(Exception("Photos permission was denied.")))
        }
    }
}

private fun exportImageToFiles(
    bytes: ByteArray,
    suggestedFileName: String,
    onComplete: (Result<String>) -> Unit,
) {
    val tempUrl = NSURL.fileURLWithPath("${NSTemporaryDirectory()}$suggestedFileName")
    if (!bytes.toNSData().writeToURL(tempUrl, atomically = true)) {
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

private class ImagePickerDelegate(
    val onImagePicked: (SelectedImage?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onImagePicked(null)
        IosUiHost.imagePickerDelegate = null
    }

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val bytes = image?.let { UIImageJPEGRepresentation(it, 0.82) }?.toByteArray()
        picker.dismissViewControllerAnimated(true, completion = null)
        onImagePicked(
            bytes?.let {
                SelectedImage(
                    bytes = it,
                    mimeType = "image/jpeg",
                    fileName = "image.jpg",
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
private fun ByteArray.toNSData(): NSData =
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
