package org.ascilizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val ConversionApiUrl =
    "https://ahbeub0wad.execute-api.eu-west-2.amazonaws.com/Prod/convert-and-save"

private enum class AppScreen {
    Home,
    Settings,
    Result,
}

private data class ConversionResult(
    val url: String,
    val imageBytes: ByteArray,
)

@Composable
fun App() {
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = Color(0xFF07131C),
            surface = Color(0xFF0F1E2B),
            surfaceVariant = Color(0xFF163247),
            primary = Color(0xFF58D2FF),
            secondary = Color(0xFF76E2C8),
            tertiary = Color(0xFFFFCF70),
            onBackground = Color(0xFFF1F7FB),
            onSurface = Color(0xFFF1F7FB),
            onPrimary = Color(0xFF032235),
        )
    } else {
        lightColorScheme(
            background = Color(0xFFF6FBFF),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE3F4FF),
            primary = Color(0xFF00649A),
            secondary = Color(0xFF0A9876),
            tertiary = Color(0xFFC98100),
            onBackground = Color(0xFF10202C),
            onSurface = Color(0xFF10202C),
            onPrimary = Color(0xFFFFFFFF),
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Home) }
        var settings by rememberSaveable {
            mutableStateOf(AppSettings())
        }
        var selectedImage by remember { mutableStateOf<SelectedImage?>(null) }
        var result by remember { mutableStateOf<ConversionResult?>(null) }
        var busyMessage by remember { mutableStateOf<String?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var resultBackdropInverted by rememberSaveable { mutableStateOf(false) }
        var saveMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        val imagePicker = rememberImagePicker { image ->
            image?.let {
                selectedImage = it
                result = null
                saveMessage = null
                errorMessage = null
                currentScreen = AppScreen.Home
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AtmosphericBackdrop()
                when (currentScreen) {
                    AppScreen.Home -> HomeScreen(
                        selectedPreviewBytes = selectedImage?.bytes,
                        settings = settings,
                        busyMessage = busyMessage,
                        errorMessage = errorMessage,
                        onPickImage = { imagePicker.launch() },
                        onOpenSettings = { currentScreen = AppScreen.Settings },
                        onConvert = {
                            val image = selectedImage ?: return@HomeScreen
                            scope.launch {
                                busyMessage = "Converting your image"
                                errorMessage = null
                                saveMessage = null
                                val uploadResult = uploadConvertedImage(
                                    apiUrl = ConversionApiUrl,
                                    image = image,
                                    settings = settings,
                                )
                                uploadResult.fold(
                                    onSuccess = { url ->
                                        busyMessage = "Downloading preview"
                                        val bytesResult = fetchRemoteBytes(url)
                                        bytesResult.fold(
                                            onSuccess = { bytes ->
                                                result = ConversionResult(url, bytes)
                                                resultBackdropInverted = false
                                                currentScreen = AppScreen.Result
                                            },
                                            onFailure = { throwable ->
                                                errorMessage = throwable.message ?: "Couldn't load the converted image."
                                            },
                                        )
                                    },
                                    onFailure = { throwable ->
                                        errorMessage = throwable.message ?: "Conversion failed."
                                    },
                                )
                                busyMessage = null
                            }
                        },
                    )

                    AppScreen.Settings -> SettingsScreen(
                        settings = settings,
                        onBack = { currentScreen = AppScreen.Home },
                        onSettingsChange = {
                            settings = it
                            errorMessage = null
                        },
                    )

                    AppScreen.Result -> ResultScreen(
                        resultPreviewBytes = result?.imageBytes,
                        resultPreviewUrl = result?.url,
                        textColor = settings.color,
                        invertBackground = resultBackdropInverted,
                        busyMessage = busyMessage,
                        saveMessage = saveMessage,
                        errorMessage = errorMessage,
                        onInvertBackgroundChange = { resultBackdropInverted = it },
                        onBack = { currentScreen = AppScreen.Home },
                        onSave = {
                            val imageResult = result ?: return@ResultScreen
                            scope.launch {
                                busyMessage = "Saving your image"
                                saveResultImage(
                                    bytes = imageResult.imageBytes,
                                    suggestedFileName = "ascilizer-result.png",
                                ).fold(
                                    onSuccess = { message ->
                                        saveMessage = message
                                        errorMessage = null
                                    },
                                    onFailure = { throwable ->
                                        errorMessage = throwable.message ?: "Couldn't save the image."
                                    },
                                )
                                busyMessage = null
                            }
                        },
                    )
                }

                AnimatedVisibility(
                    visible = busyMessage != null,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    LoadingCard(label = busyMessage.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun AtmosphericBackdrop() {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.background,
        ),
        start = Offset.Zero,
        end = Offset.Infinite,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val accent = primary.copy(alpha = 0.14f)
                val warm = tertiary.copy(alpha = 0.1f)
                onDrawBehind {
                    drawRect(gradient)
                    drawCircle(
                        color = accent,
                        radius = size.minDimension * 0.36f,
                        center = Offset(size.width * 0.82f, size.height * 0.18f),
                    )
                    drawCircle(
                        color = warm,
                        radius = size.minDimension * 0.3f,
                        center = Offset(size.width * 0.15f, size.height * 0.78f),
                    )
                }
            },
    ) {
        val patternColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val patternBrush = Brush.radialGradient(
                colors = listOf(
                    patternColor,
                    Color.Transparent,
                ),
                radius = size.minDimension * 0.08f,
                tileMode = TileMode.Decal,
            )
            for (x in 0..7) {
                for (y in 0..14) {
                    drawCircle(
                        brush = patternBrush,
                        radius = size.minDimension * 0.015f,
                        center = Offset(
                            x = size.width * (0.08f + (x * 0.13f)),
                            y = size.height * (0.05f + (y * 0.07f)),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    selectedPreviewBytes: ByteArray?,
    settings: AppSettings,
    busyMessage: String?,
    errorMessage: String?,
    onPickImage: () -> Unit,
    onOpenSettings: () -> Unit,
    onConvert: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            HeaderBlock(
                eyebrow = "Pixel to glyph art",
                title = "Ascilizer",
                subtitle = "Turn photos into bold text-image renders with the same API flow as the mobile original.",
            )
            Spacer(modifier = Modifier.height(18.dp))
            PreviewCard(
                imageBytes = selectedPreviewBytes,
                textColor = settings.color.previewColor,
            )
            Spacer(modifier = Modifier.height(16.dp))
            StatusCard(
                sizeLabel = settings.sizeLabel,
                colorLabel = settings.color.label,
                inverted = settings.inverted,
                errorMessage = errorMessage,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selectedPreviewBytes != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentButton(
                        label = if (busyMessage == null) "Convert" else busyMessage,
                        enabled = busyMessage == null,
                        modifier = Modifier.weight(1f),
                        onClick = onConvert,
                    )
                    OutlineActionButton(
                        label = "Settings",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSettings,
                    )
                }
            }
            OutlineActionButton(label = "Select a photo", onClick = onPickImage)
            if (selectedPreviewBytes == null) {
                OutlineActionButton(label = "Settings", onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        HeaderBlock(
            eyebrow = "Output tuning",
            title = "Settings",
            subtitle = "Keep the same size, color, and inversion controls, with a refreshed layout for system light and dark themes.",
        )

        SettingsCard(title = "Size") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SizeOptions.forEach { option ->
                    SelectionChip(
                        label = option.label,
                        selected = settings.size == option.apiValue,
                        onClick = { onSettingsChange(settings.copy(size = option.apiValue)) },
                    )
                }
            }
        }

        SettingsCard(title = "Text Color") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AsciiColorOption.entries.forEach { option ->
                    ColorChip(
                        label = option.label,
                        color = option.previewColor,
                        selected = settings.color == option,
                        onClick = { onSettingsChange(settings.copy(color = option)) },
                    )
                }
            }
        }

        SettingsCard(title = "Inverted") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Invert the glyph rendering sent to the API",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Switch(
                    checked = settings.inverted,
                    onCheckedChange = { onSettingsChange(settings.copy(inverted = it)) },
                )
            }
        }

        AccentButton(label = "Save", onClick = onBack)
    }
}

@Composable
private fun ResultScreen(
    resultPreviewBytes: ByteArray?,
    resultPreviewUrl: String?,
    textColor: AsciiColorOption,
    invertBackground: Boolean,
    busyMessage: String?,
    saveMessage: String?,
    errorMessage: String?,
    onInvertBackgroundChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var scale by rememberSaveable { mutableStateOf(1f) }
    var offsetX by rememberSaveable { mutableStateOf(0f) }
    var offsetY by rememberSaveable { mutableStateOf(0f) }
    val animatedScale by animateFloatAsState(scale)
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HeaderBlock(
                eyebrow = "Converted output",
                title = "Preview",
                subtitle = "Pinch and drag the exported artwork before saving it back out.",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(textColor.resultBackground(invertBackground))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                    .transformable(transformableState),
                contentAlignment = Alignment.Center,
            ) {
                if (resultPreviewBytes != null) {
                    PlatformPreviewImage(
                        bytes = resultPreviewBytes,
                        remoteUrl = resultPreviewUrl,
                        contentDescription = "Converted image",
                        modifier = Modifier.graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    )
                } else {
                    Text(
                        text = "The converted image preview will appear here.",
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            SettingsCard(title = "Display") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Invert preview background",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = invertBackground,
                        onCheckedChange = onInvertBackgroundChange,
                    )
                }
            }

            if (!saveMessage.isNullOrBlank()) {
                FeedbackCard(text = saveMessage, accent = MaterialTheme.colorScheme.secondary)
            }
            if (!errorMessage.isNullOrBlank()) {
                FeedbackCard(text = errorMessage, accent = MaterialTheme.colorScheme.tertiary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentButton(
                label = if (busyMessage == null) "Save image" else busyMessage,
                enabled = busyMessage == null,
                onClick = onSave,
            )
            OutlineActionButton(label = "Back", onClick = onBack)
        }
    }
}

@Composable
private fun HeaderBlock(
    eyebrow: String,
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = eyebrow.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
            ),
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
        )
    }
}

@Composable
private fun PreviewCard(
    imageBytes: ByteArray?,
    textColor: Color,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        val side = maxWidth - 8.dp
        Box(
            modifier = Modifier
                .size(side)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageBytes == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "ASC",
                        color = textColor,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                        ),
                    )
                    Text(
                        text = "Ascilizer",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "Select a photo to start",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            } else {
                PlatformPreviewImage(
                    bytes = imageBytes,
                    remoteUrl = null,
                    contentDescription = "Selected image",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    sizeLabel: String,
    colorLabel: String,
    inverted: Boolean,
    errorMessage: String?,
) {
    SettingsCard(title = "Current settings") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusRow(label = "Size", value = sizeLabel)
            StatusRow(label = "Text color", value = colorLabel)
            StatusRow(label = "Inverted", value = if (inverted) "On" else "Off")
            if (!errorMessage.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), RoundedCornerShape(28.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            content()
        },
    )
}

@Composable
private fun SelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            ),
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ColorChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            )
            .border(
                width = if (selected) 1.6.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, CircleShape),
        )
        Text(text = label, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FeedbackCard(
    text: String,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(accent, CircleShape),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text)
    }
}

@Composable
private fun LoadingCard(label: String) {
    Surface(
        modifier = Modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(26.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 10.dp,
        shape = RoundedCornerShape(26.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun AccentButton(
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun OutlineActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 16.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

private val SizeOptions = listOf(
    SizeOption("Image size", "0"),
    SizeOption("Small", "50"),
    SizeOption("Medium", "100"),
    SizeOption("Large", "200"),
)

private data class SizeOption(
    val label: String,
    val apiValue: String,
)

private val AppSettings.sizeLabel: String
    get() = SizeOptions.firstOrNull { it.apiValue == size }?.label ?: "Image size"

private val AsciiColorOption.previewColor: Color
    get() = when (this) {
        AsciiColorOption.Black -> Color.Black
        AsciiColorOption.White -> Color.White
        AsciiColorOption.Red -> Color(0xFFC83C3C)
        AsciiColorOption.Green -> Color(0xFF219653)
        AsciiColorOption.Blue -> Color(0xFF1E73D8)
        AsciiColorOption.Yellow -> Color(0xFFFFC233)
        AsciiColorOption.LightGray -> Color(0xFFD0D5DB)
        AsciiColorOption.DarkGray -> Color(0xFF4B5563)
    }

private fun AsciiColorOption.resultBackground(invert: Boolean): Color {
    val darkPreferred = when (this) {
        AsciiColorOption.Black,
        AsciiColorOption.DarkGray,
        AsciiColorOption.Red,
        AsciiColorOption.Green,
        AsciiColorOption.Blue -> false

        AsciiColorOption.White,
        AsciiColorOption.LightGray,
        AsciiColorOption.Yellow -> true
    }

    return when {
        darkPreferred && invert -> Color.White
        darkPreferred -> Color.Black
        invert -> Color.Black
        else -> Color.White
    }
}
