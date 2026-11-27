package com.kexora.soniclet.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

enum class EditorTab {
    CROP_TRANSFORM,
    ADJUST_TONE,
    FILTERS
}

enum class FilterPreset(val displayName: String) {
    ORIGINAL("Original"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    MONOCHROME("B&W"),
    CINEMATIC("Dramatic"),
    VINTAGE("Vintage")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverArtEditorDialog(
    imageUri: Uri,
    title: String = "Edit Cover Art",
    isWallpaperMode: Boolean = false,
    onDismissRequest: () -> Unit,
    onApplyEditedArt: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingSource by remember { mutableStateOf(true) }
    var isProcessingOutput by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Aspect ratio state (defaults to 9:16 for phone wallpaper or 1:1 for cover art)
    var selectedAspectRatio by remember { mutableFloatStateOf(if (isWallpaperMode) (9f / 16f) else 1f) }
    var selectedAspectLabel by remember { mutableStateOf(if (isWallpaperMode) "9:16" else "1:1") }
    var currentViewportWidthPx by remember { mutableFloatStateOf(320f) }
    var currentViewportHeightPx by remember { mutableFloatStateOf(320f) }

    // Transform states
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationSteps by remember { mutableIntStateOf(0) } // Multiples of 90 degrees
    var fineRotation by remember { mutableFloatStateOf(0f) } // -45 to +45 deg
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }

    // Color & Tone states
    var brightness by remember { mutableFloatStateOf(0f) } // -50 to +50
    var contrast by remember { mutableFloatStateOf(1f) } // 0.5 to 1.8
    var saturation by remember { mutableFloatStateOf(1f) } // 0 to 2
    var selectedFilter by remember { mutableStateOf(FilterPreset.ORIGINAL) }

    var currentTab by remember { mutableStateOf(EditorTab.CROP_TRANSFORM) }

    // Load source bitmap
    LaunchedEffect(imageUri) {
        isLoadingSource = true
        loadError = null
        try {
            val bmp = withContext(Dispatchers.IO) {
                loadOptimizedBitmap(context, imageUri)
            }
            if (bmp != null) {
                sourceBitmap = bmp
            } else {
                loadError = "Unable to load the selected image."
            }
        } catch (e: Exception) {
            Log.e("CoverArtEditor", "Error loading image", e)
            loadError = "Error reading image: ${e.localizedMessage}"
        } finally {
            isLoadingSource = false
        }
    }

    fun resetTransform() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        rotationSteps = 0
        fineRotation = 0f
        flipH = false
        flipV = false
    }

    fun resetTone() {
        brightness = 0f
        contrast = 1f
        saturation = 1f
        selectedFilter = FilterPreset.ORIGINAL
    }

    fun resetAll() {
        resetTransform()
        resetTone()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isProcessingOutput,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0C0C0F)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismissRequest,
                        enabled = !isProcessingOutput,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                        color = Color.White
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                enabled = !isProcessingOutput && sourceBitmap != null,
                                onClick = { resetAll() }
                            )
                    ) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                            color = if (sourceBitmap != null) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                if (isLoadingSource) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Loading image...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.LightGray
                            )
                        }
                    }
                } else if (loadError != null || sourceBitmap == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = loadError ?: "Failed to open image",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onDismissRequest, shape = CircleShape) {
                                Text("Close")
                            }
                        }
                    }
                } else {
                    val bitmap = sourceBitmap!!

                    // Preview Area with 100% mathematically constrained responsive viewport
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF09090C))
                            .padding(top = 6.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val padX = 16.dp
                        val padY = 6.dp
                        val availW = (maxWidth - padX * 2).coerceAtLeast(80.dp)
                        val availH = (maxHeight - padY * 2).coerceAtLeast(80.dp)
                        val containerAspect = availW / availH

                        val (viewW, viewH) = if (selectedAspectRatio <= containerAspect) {
                            (availH * selectedAspectRatio) to availH
                        } else {
                            availW to (availW / selectedAspectRatio)
                        }

                        Box(
                            modifier = Modifier
                                .size(width = viewW, height = viewH)
                                .clip(RoundedCornerShape(22.dp))
                                .border(
                                    width = 2.dp,
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    ),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .background(Color.Black)
                                .clipToBounds()
                                .pointerInput(selectedAspectRatio) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.6f, 4.0f)
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val density = LocalDensity.current
                            val viewportWidthPx = with(density) { viewW.toPx() }
                            val viewportHeightPx = with(density) { viewH.toPx() }
                            LaunchedEffect(viewportWidthPx, viewportHeightPx) {
                                currentViewportWidthPx = viewportWidthPx
                                currentViewportHeightPx = viewportHeightPx
                            }

                            // Render transformed Canvas
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawIntoCanvas { composeCanvas ->
                                    val nativeCanvas = composeCanvas.nativeCanvas

                                    // Build Color Matrix for filter & tone
                                    val colorMatrix = computeColorMatrix(
                                        brightness = brightness,
                                        contrast = contrast,
                                        saturation = saturation,
                                        preset = selectedFilter
                                    )
                                    val paint = Paint().apply {
                                        isAntiAlias = true
                                        isFilterBitmap = true
                                        if (colorMatrix != null) {
                                            colorFilter = ColorMatrixColorFilter(colorMatrix)
                                        }
                                    }

                                    // Calculate center scaling
                                    val bmpWidth = bitmap.width.toFloat()
                                    val bmpHeight = bitmap.height.toFloat()

                                    val baseScale = max(
                                        viewportWidthPx / bmpWidth,
                                        viewportHeightPx / bmpHeight
                                    )

                                    val matrix = Matrix().apply {
                                        postTranslate(-bmpWidth / 2f, -bmpHeight / 2f)

                                        val sx = if (flipH) -1f else 1f
                                        val sy = if (flipV) -1f else 1f
                                        postScale(sx, sy)

                                        val totalRotation = (rotationSteps * 90f) + fineRotation
                                        postRotate(totalRotation)

                                        val finalScale = baseScale * scale
                                        postScale(finalScale, finalScale)

                                        postTranslate(
                                            (viewportWidthPx / 2f) + offsetX,
                                            (viewportHeightPx / 2f) + offsetY
                                        )
                                    }

                                    nativeCanvas.drawBitmap(bitmap, matrix, paint)
                                }

                                // Rule of Thirds subtle crop grid
                                val gridColor = Color.White.copy(alpha = 0.22f)
                                val w = size.width
                                val h = size.height

                                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1f)
                                drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1f)
                                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1f)
                                drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1f)
                            }
                        }

                        // Floating gesture tip capsule
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "Pinch to zoom • Drag to pan",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Bottom Control Sheet
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        color = Color(0xFF15151A),
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Modern Compact Segmented Pill Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(Color(0xFF22222A))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                EditorTab.values().forEach { tab ->
                                    val isSelected = currentTab == tab
                                    val (tabLabel, tabIcon) = when (tab) {
                                        EditorTab.CROP_TRANSFORM -> "Crop & Rotate" to Icons.Default.CropRotate
                                        EditorTab.ADJUST_TONE -> "Adjust" to Icons.Default.Tune
                                        EditorTab.FILTERS -> "Filters" to Icons.Default.AutoFixHigh
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { currentTab = tab },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = tabIcon,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.Gray,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = tabLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 11.sp
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.LightGray
                                            )
                                        }
                                    }
                                }
                            }

                            // Tab Content Area (Clean, scrollable, dynamically sized, never pushes buttons off)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp, max = 125.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                when (currentTab) {
                                    EditorTab.CROP_TRANSFORM -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Aspect Ratio Selector Pills
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val ratioOptions = if (isWallpaperMode) {
                                                    listOf(
                                                        "9:16 (Phone)" to (9f / 16f),
                                                        "3:4 (Portrait)" to (3f / 4f),
                                                        "1:1 (Square)" to 1f,
                                                        "16:9 (Landscape)" to (16f / 9f),
                                                        "Original" to (bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f))
                                                    )
                                                } else {
                                                    listOf(
                                                        "1:1 (Square)" to 1f,
                                                        "4:3" to (4f / 3f),
                                                        "Original" to (bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f))
                                                    )
                                                }

                                                ratioOptions.forEach { (label, ratio) ->
                                                    val isSelected = selectedAspectLabel == label
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF222228),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                                                        ),
                                                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                                            selectedAspectLabel = label
                                                            selectedAspectRatio = ratio
                                                            offsetX = 0f
                                                            offsetY = 0f
                                                            scale = 1f
                                                        }
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                fontSize = 11.sp
                                                            ),
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Quick Transform Actions Row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                ActionButton(
                                                    icon = Icons.AutoMirrored.Filled.RotateLeft,
                                                    label = "Rotate L",
                                                    onClick = { rotationSteps = (rotationSteps - 1 + 4) % 4 }
                                                )
                                                ActionButton(
                                                    icon = Icons.AutoMirrored.Filled.RotateRight,
                                                    label = "Rotate R",
                                                    onClick = { rotationSteps = (rotationSteps + 1) % 4 }
                                                )
                                                ActionButton(
                                                    icon = Icons.Default.Flip,
                                                    label = "Flip H",
                                                    isActive = flipH,
                                                    onClick = { flipH = !flipH }
                                                )
                                                ActionButton(
                                                    icon = Icons.Default.SwapVert,
                                                    label = "Flip V",
                                                    isActive = flipV,
                                                    onClick = { flipV = !flipV }
                                                )
                                                ActionButton(
                                                    icon = Icons.Default.CenterFocusStrong,
                                                    label = "Center",
                                                    onClick = {
                                                        offsetX = 0f
                                                        offsetY = 0f
                                                        scale = 1f
                                                    }
                                                )
                                            }

                                            // Fine Angle & Zoom Sliders (Compact)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Angle: ${fineRotation.toInt()}°",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.LightGray,
                                                    modifier = Modifier.width(62.dp)
                                                )
                                                Slider(
                                                    value = fineRotation,
                                                    onValueChange = { fineRotation = it },
                                                    valueRange = -45f..45f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (fineRotation != 0f) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color(0xFF2C2C32),
                                                        modifier = Modifier.clip(CircleShape).clickable { fineRotation = 0f }
                                                    ) {
                                                        Icon(Icons.Default.Refresh, contentDescription = "Zero angle", tint = Color.LightGray, modifier = Modifier.size(20.dp).padding(3.dp))
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Zoom: ${String.format("%.1fx", scale)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.LightGray,
                                                    modifier = Modifier.width(62.dp)
                                                )
                                                Slider(
                                                    value = scale,
                                                    onValueChange = { scale = it },
                                                    valueRange = 0.8f..3.0f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (scale != 1.0f) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color(0xFF2C2C32),
                                                        modifier = Modifier.clip(CircleShape).clickable { scale = 1.0f }
                                                    ) {
                                                        Icon(Icons.Default.Refresh, contentDescription = "Reset zoom", tint = Color.LightGray, modifier = Modifier.size(20.dp).padding(3.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    EditorTab.ADJUST_TONE -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Brightness Slider
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Brightness6, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Brightness", style = MaterialTheme.typography.labelSmall, color = Color.LightGray, modifier = Modifier.width(66.dp))
                                                Slider(
                                                    value = brightness,
                                                    onValueChange = { brightness = it },
                                                    valueRange = -40f..40f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = if (brightness > 0) "+${brightness.toInt()}" else "${brightness.toInt()}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    modifier = Modifier.width(32.dp),
                                                    textAlign = TextAlign.End
                                                )
                                            }

                                            // Contrast Slider
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Contrast, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Contrast", style = MaterialTheme.typography.labelSmall, color = Color.LightGray, modifier = Modifier.width(66.dp))
                                                Slider(
                                                    value = contrast,
                                                    onValueChange = { contrast = it },
                                                    valueRange = 0.5f..1.8f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${(contrast * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    modifier = Modifier.width(32.dp),
                                                    textAlign = TextAlign.End
                                                )
                                            }

                                            // Saturation Slider
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Saturation", style = MaterialTheme.typography.labelSmall, color = Color.LightGray, modifier = Modifier.width(66.dp))
                                                Slider(
                                                    value = saturation,
                                                    onValueChange = { saturation = it },
                                                    valueRange = 0.0f..2.0f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${(saturation * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    modifier = Modifier.width(32.dp),
                                                    textAlign = TextAlign.End
                                                )
                                            }
                                        }
                                    }

                                    EditorTab.FILTERS -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilterPreset.values().forEach { preset ->
                                                val isSelected = selectedFilter == preset
                                                Surface(
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF222228),
                                                    border = BorderStroke(
                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                                                    ),
                                                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { selectedFilter = preset }
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = when (preset) {
                                                                FilterPreset.ORIGINAL -> Icons.Default.FilterNone
                                                                FilterPreset.VIVID -> Icons.Default.Flare
                                                                FilterPreset.WARM -> Icons.Default.WbSunny
                                                                FilterPreset.COOL -> Icons.Default.AcUnit
                                                                FilterPreset.MONOCHROME -> Icons.Default.FilterBAndW
                                                                FilterPreset.CINEMATIC -> Icons.Default.MovieFilter
                                                                FilterPreset.VINTAGE -> Icons.Default.CameraRoll
                                                            },
                                                            contentDescription = null,
                                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = preset.displayName,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                fontSize = 11.sp
                                                            ),
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Apply Button (Fully visible, comfortable touch target, no clipping!)
                            Button(
                                onClick = {
                                    if (isProcessingOutput) return@Button
                                    isProcessingOutput = true
                                    scope.launch {
                                        try {
                                            val outputUri = withContext(Dispatchers.IO) {
                                                renderAndSaveCroppedBitmap(
                                                    context = context,
                                                    sourceBitmap = bitmap,
                                                    scale = scale,
                                                    offsetX = offsetX,
                                                    offsetY = offsetY,
                                                    rotationSteps = rotationSteps,
                                                    fineRotation = fineRotation,
                                                    flipH = flipH,
                                                    flipV = flipV,
                                                    brightness = brightness,
                                                    contrast = contrast,
                                                    saturation = saturation,
                                                    preset = selectedFilter,
                                                    targetAspect = selectedAspectRatio,
                                                    viewportWidthPx = currentViewportWidthPx,
                                                    viewportHeightPx = currentViewportHeightPx,
                                                    isWallpaperMode = isWallpaperMode
                                                )
                                            }
                                            if (outputUri != null) {
                                                onApplyEditedArt(outputUri)
                                            } else {
                                                Toast.makeText(context, if (isWallpaperMode) "Failed to export wallpaper" else "Failed to export edited cover art", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Throwable) {
                                            Log.e("CoverArtEditor", "Error rendering edited art", e)
                                            Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isProcessingOutput = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = CircleShape,
                                enabled = !isProcessingOutput,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                if (isProcessingOutput) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isWallpaperMode) "Processing Wallpaper..." else "Processing Artwork...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isWallpaperMode) "Apply Background Wallpaper" else "Apply Edited Cover Art", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color(0xFF222228),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color.LightGray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Medium),
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.LightGray
            )
        }
    }
}

/**
 * Loads a downsampled bitmap to guarantee fast performance and avoid OutOfMemory errors.
 */
private fun loadOptimizedBitmap(context: Context, uri: Uri): Bitmap? {
    var input: InputStream? = null
    return try {
        input = context.contentResolver.openInputStream(uri) ?: return null
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, boundsOptions)
        input.close()

        val reqWidth = 1400
        val reqHeight = 1400
        var inSampleSize = 1

        if (boundsOptions.outHeight > reqHeight || boundsOptions.outWidth > reqWidth) {
            val halfHeight: Int = boundsOptions.outHeight / 2
            val halfWidth: Int = boundsOptions.outWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        input = context.contentResolver.openInputStream(uri) ?: return null
        BitmapFactory.decodeStream(input, null, decodeOptions)
    } catch (e: Throwable) {
        Log.e("CoverArtEditor", "Failed decoding source bitmap", e)
        null
    } finally {
        try {
            input?.close()
        } catch (_: Exception) {}
    }
}

/**
 * Computes ColorMatrix for brightness, contrast, saturation, and preset filters.
 */
private fun computeColorMatrix(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    preset: FilterPreset
): ColorMatrix? {
    val cm = ColorMatrix()

    // Base Saturation
    cm.setSaturation(saturation)

    // Contrast & Brightness scale
    // Formula: (color - 128) * contrast + 128 + brightness
    val scale = contrast
    val translate = (-128f * (contrast - 1f)) + brightness
    val cbMatrix = ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    cm.postConcat(cbMatrix)

    // Apply Preset Filter Matrix
    when (preset) {
        FilterPreset.ORIGINAL -> { /* no extra change */ }
        FilterPreset.VIVID -> {
            val vivid = ColorMatrix().apply { setSaturation(1.35f) }
            val vividCB = ColorMatrix(
                floatArrayOf(
                    1.1f, 0f, 0f, 0f, 5f,
                    0f, 1.1f, 0f, 0f, 5f,
                    0f, 0f, 1.1f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            vivid.postConcat(vividCB)
            cm.postConcat(vivid)
        }
        FilterPreset.WARM -> {
            val warm = ColorMatrix(
                floatArrayOf(
                    1.15f, 0f, 0f, 0f, 15f,
                    0f, 1.05f, 0f, 0f, 8f,
                    0f, 0f, 0.88f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(warm)
        }
        FilterPreset.COOL -> {
            val cool = ColorMatrix(
                floatArrayOf(
                    0.9f, 0f, 0f, 0f, -5f,
                    0f, 1.02f, 0f, 0f, 4f,
                    0f, 0f, 1.2f, 0f, 18f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(cool)
        }
        FilterPreset.MONOCHROME -> {
            val mono = ColorMatrix().apply { setSaturation(0.0f) }
            val contrastBoost = ColorMatrix(
                floatArrayOf(
                    1.2f, 0f, 0f, 0f, -10f,
                    0f, 1.2f, 0f, 0f, -10f,
                    0f, 0f, 1.2f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            mono.postConcat(contrastBoost)
            cm.postConcat(mono)
        }
        FilterPreset.CINEMATIC -> {
            val cinema = ColorMatrix(
                floatArrayOf(
                    1.12f, 0f, 0f, 0f, -5f,
                    0f, 1.18f, 0f, 0f, -2f,
                    0f, 0f, 1.28f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(cinema)
        }
        FilterPreset.VINTAGE -> {
            val vintage = ColorMatrix(
                floatArrayOf(
                    0.9f, 0.1f, 0.1f, 0f, 25f,
                    0.05f, 0.85f, 0.1f, 0f, 15f,
                    0.1f, 0.1f, 0.75f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(vintage)
        }
    }

    return cm
}

/**
 * Renders the final cropped, rotated, filtered image at crisp resolution and saves to cache.
 */
private fun renderAndSaveCroppedBitmap(
    context: Context,
    sourceBitmap: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    rotationSteps: Int,
    fineRotation: Float,
    flipH: Boolean,
    flipV: Boolean,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    preset: FilterPreset,
    targetAspect: Float = 1f,
    viewportWidthPx: Float = 320f,
    viewportHeightPx: Float = 320f,
    isWallpaperMode: Boolean = false
): Uri? {
    val targetWidth: Int
    val targetHeight: Int
    if (targetAspect <= 1f) {
        // Portrait or Square (e.g. 9:16 -> 1080 x 1920)
        targetWidth = 1080
        targetHeight = (1080f / targetAspect).toInt().coerceIn(600, 3840)
    } else {
        // Landscape (e.g. 16:9 -> 1920 x 1080)
        targetHeight = 1080
        targetWidth = (1080f * targetAspect).toInt().coerceIn(600, 3840)
    }

    val outBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(outBitmap)

    val colorMatrix = computeColorMatrix(brightness, contrast, saturation, preset)
    val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
        if (colorMatrix != null) {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
    }

    val bmpWidth = sourceBitmap.width.toFloat()
    val bmpHeight = sourceBitmap.height.toFloat()

    val baseScale = max(
        targetWidth.toFloat() / bmpWidth,
        targetHeight.toFloat() / bmpHeight
    )

    val matrix = Matrix().apply {
        // 1. Center the source bitmap
        postTranslate(-bmpWidth / 2f, -bmpHeight / 2f)

        // 2. Apply flips
        val sx = if (flipH) -1f else 1f
        val sy = if (flipV) -1f else 1f
        postScale(sx, sy)

        // 3. Apply total rotation
        val totalRotation = (rotationSteps * 90f) + fineRotation
        postRotate(totalRotation)

        // 4. Scale with user zoom factor
        val finalScale = baseScale * scale
        postScale(finalScale, finalScale)

        // 5. Translate to output center with exact pan ratio relative to viewport
        val panScaleX = if (viewportWidthPx > 0) targetWidth.toFloat() / viewportWidthPx else 1f
        val panScaleY = if (viewportHeightPx > 0) targetHeight.toFloat() / viewportHeightPx else 1f
        postTranslate(
            (targetWidth / 2f) + (offsetX * panScaleX),
            (targetHeight / 2f) + (offsetY * panScaleY)
        )
    }

    canvas.drawBitmap(sourceBitmap, matrix, paint)

    // Save to temp JPEG in cache directory
    val prefix = if (isWallpaperMode) "edited_wallpaper_" else "edited_cover_art_"
    val cacheFile = File(context.cacheDir, "${prefix}${System.currentTimeMillis()}.jpg")
    return try {
        FileOutputStream(cacheFile).use { out ->
            outBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.flush()
        }
        Uri.fromFile(cacheFile)
    } catch (e: Throwable) {
        Log.e("CoverArtEditor", "Error writing edited bitmap to file", e)
        null
    } finally {
        outBitmap.recycle()
    }
}
