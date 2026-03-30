package org.ascilizer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class SelectedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
)

data class AppSettings(
    val size: String = "0",
    val color: AsciiColorOption = AsciiColorOption.DarkGray,
    val inverted: Boolean = false,
)

enum class AsciiColorOption(
    val label: String,
    val apiValue: String,
) {
    Black("Black", "black"),
    White("White", "white"),
    Red("Red", "red"),
    Green("Green", "green"),
    Blue("Blue", "blue"),
    Yellow("Yellow", "yellow"),
    LightGray("Light Gray", "light-gray"),
    DarkGray("Dark Gray", "dark-gray"),
}

interface ImagePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberImagePicker(onImagePicked: (SelectedImage?) -> Unit): ImagePickerLauncher

expect suspend fun saveResultImage(
    bytes: ByteArray,
    suggestedFileName: String,
): Result<String>

@Composable
expect fun PlatformPreviewImage(
    bytes: ByteArray,
    contentDescription: String?,
    modifier: Modifier = Modifier,
)

expect fun isPreviewRenderable(bytes: ByteArray): Boolean
