package com.kexora.soniclet.ui.components.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Ultra-compact, clean, and dynamic Custom Color Picker Dialog.
 * Adapts seamlessly to all screen sizes and orientations without clipping or overflowing.
 * Retains 100% of underlying logic (HSV, Swatches, Spectrum, Sliders, Hex copy, Presets).
 */
@Composable
fun AdvancedColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Swatches, 1: Spectrum

    // HSV State
    val initialHsv = remember(initialColor) {
        val arr = FloatArray(3)
        AndroidColor.colorToHSV(initialColor.toArgb(), arr)
        arr
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor = remember(hue, saturation, value) {
        Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    val fullColor = remember(hue, saturation) {
        Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f)))
    }

    val animatedColor by animateColorAsState(targetValue = currentColor, animationSpec = tween(120), label = "color_anim")

    fun applyColor(c: Color) {
        val tmp = FloatArray(3)
        AndroidColor.colorToHSV(c.toArgb(), tmp)
        hue = tmp[0]
        saturation = tmp[1]
        value = tmp[2]
    }

    val argb = currentColor.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val hex = String.format("#%06X", 0xFFFFFF and argb)

    fun copyHex() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Color Hex", hex))
        Toast.makeText(context, "$hex copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with live preview, title, and compact tab pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { copyHex() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(animatedColor)
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Color Picker",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = hex,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Compact Segmented Pill Tabs
                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactTabPill(
                            label = "Swatches",
                            icon = Icons.Rounded.GridView,
                            isSelected = selectedTab == 0,
                            onClick = { selectedTab = 0 }
                        )
                        CompactTabPill(
                            label = "Spectrum",
                            icon = Icons.Rounded.Gradient,
                            isSelected = selectedTab == 1,
                            onClick = { selectedTab = 1 }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Color Area (Swatches vs 2D Spectrum)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                ) {
                    if (selectedTab == 0) {
                        CompactSwatchesView(
                            selectedColor = currentColor,
                            onColorPicked = { applyColor(it) }
                        )
                    } else {
                        CompactSpectrumView(
                            hue = hue,
                            saturation = saturation,
                            onPositionChanged = { h, s ->
                                hue = h
                                saturation = s
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Brightness Slider
                CompactBrightnessSlider(
                    value = value,
                    baseColor = fullColor,
                    onValueChanged = { value = it }
                )

                // Hue Slider for Spectrum fine-tuning
                if (selectedTab == 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CompactHueSlider(
                        hue = hue,
                        onHueChanged = { hue = it }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Color Info & Copy Bar
                CompactColorInfoRow(
                    currentColor = currentColor,
                    hex = hex,
                    r = r,
                    g = g,
                    b = b,
                    onCopyHex = { copyHex() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Presets & Revert Row
                CompactQuickPresetsRow(
                    onSelectPreset = { applyColor(it) },
                    onResetInitial = { applyColor(initialColor) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons (Cancel / Apply)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { onColorSelected(currentColor) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(13.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CompactSwatchesView(
    selectedColor: Color,
    onColorPicked: (Color) -> Unit
) {
    // 10 columns x 8 rows of balanced modern colors, pastels, and shades
    val swatches = remember {
        val list = mutableListOf<Color>()
        // Row 1: Neutrals & Monochrome
        val greys = listOf(
            0xFFFFFFFFL, 0xFFE0E0E0L, 0xFFBDBDBDL, 0xFF9E9E9EL, 0xFF757575L,
            0xFF616161L, 0xFF424242L, 0xFF303030L, 0xFF212121L, 0xFF000000L
        )
        greys.forEach { list.add(Color(it)) }

        // Row 2: Soft Pastels
        val pastels = listOf(
            0xFFFFCDD2L, 0xFFF8BBD0L, 0xFFE1BEE7L, 0xFFD1C4E9L, 0xFFC5CAE9L,
            0xFFBBDEFBL, 0xFFB2EBF2L, 0xFFC8E6C9L, 0xFFFFF9C4L, 0xFFFFE0B2L
        )
        pastels.forEach { list.add(Color(it)) }

        // Rows 3-7: Vibrant Hues from Red to Magenta across values
        val hues = floatArrayOf(0f, 25f, 45f, 85f, 135f, 180f, 215f, 260f, 290f, 330f)
        val valueLevels = floatArrayOf(1.0f, 0.85f, 0.70f, 0.55f, 0.40f)

        for (v in valueLevels) {
            for (h in hues) {
                list.add(Color(AndroidColor.HSVToColor(floatArrayOf(h, 0.95f, v))))
            }
        }

        // Row 8: Rich jewel & modern accents
        val jewels = listOf(
            0xFFFF1744L, 0xFFFF5252L, 0xFFFF9100L, 0xFFFFEA00L, 0xFF00E676L,
            0xFF00E5FFL, 0xFF2979FFL, 0xFF651FFFL, 0xFFD500F9L, 0xFFFF4081L
        )
        jewels.forEach { list.add(Color(it)) }
        list
    }

    val selectedArgb = selectedColor.toArgb()

    LazyVerticalGrid(
        columns = GridCells.Fixed(10),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(swatches) { color ->
            val isCurrent = (color.toArgb() == selectedArgb)
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
                    .then(
                        if (isCurrent) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp))
                        else Modifier
                    )
                    .clickable { onColorPicked(color) },
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (color == Color.White) Color.Black else Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSpectrumView(
    hue: Float,
    saturation: Float,
    onPositionChanged: (Float, Float) -> Unit
) {
    val hueColors = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newHue = (offset.x / size.width) * 360f
                    val newSat = 1f - (offset.y / size.height)
                    onPositionChanged(newHue.coerceIn(0f, 360f), newSat.coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val offset = change.position
                    val newHue = (offset.x / size.width) * 360f
                    val newSat = 1f - (offset.y / size.height)
                    onPositionChanged(newHue.coerceIn(0f, 360f), newSat.coerceIn(0f, 1f))
                }
            }
    ) {
        // Horizontal spectrum + vertical saturation gradient
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.horizontalGradient(colors = hueColors))
            drawRect(brush = Brush.verticalGradient(colors = listOf(Color.White, Color.Transparent)))
        }

        // Dual-ring cursor thumb
        Canvas(modifier = Modifier.fillMaxSize()) {
            val thumbX = (hue / 360f) * size.width
            val thumbY = (1f - saturation) * size.height

            // Outer dark drop shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.45f),
                radius = 10.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            // Crisp white ring
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            // Color core
            drawCircle(
                color = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f))),
                radius = 5.5.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
        }
    }
}

@Composable
private fun CompactBrightnessSlider(
    value: Float,
    baseColor: Color,
    onValueChanged: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.BrightnessMedium,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Track with gradient
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onValueChanged((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        onValueChanged((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(brush = Brush.horizontalGradient(colors = listOf(Color.Black, baseColor)))
            }

            // Draggable thumb indicator
            Canvas(modifier = Modifier.fillMaxSize()) {
                val thumbX = (value * size.width).coerceIn(7.dp.toPx(), size.width - 7.dp.toPx())
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = 7.dp.toPx(),
                    center = Offset(thumbX, size.height / 2f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(thumbX, size.height / 2f)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Compact Value Badge
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "${(value * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun CompactHueSlider(
    hue: Float,
    onHueChanged: (Float) -> Unit
) {
    val hueColors = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.ColorLens,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onHueChanged(((offset.x / size.width) * 360f).coerceIn(0f, 360f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        onHueChanged(((change.position.x / size.width) * 360f).coerceIn(0f, 360f))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(brush = Brush.horizontalGradient(colors = hueColors))
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val thumbX = ((hue / 360f) * size.width).coerceIn(7.dp.toPx(), size.width - 7.dp.toPx())
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = 7.dp.toPx(),
                    center = Offset(thumbX, size.height / 2f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(thumbX, size.height / 2f)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(5.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "${hue.toInt()}°",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun CompactColorInfoRow(
    currentColor: Color,
    hex: String,
    r: Int,
    g: Int,
    b: Int,
    onCopyHex: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Live Color Preview Tile
            Box(
                modifier = Modifier
                    .size(34.dp, 26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(currentColor)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            )

            // Clickable Hex Pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.clickable { onCopyHex() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hex,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy Hex",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            // RGB Badges
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                RgbPill(label = "R", value = r)
                RgbPill(label = "G", value = g)
                RgbPill(label = "B", value = b)
            }
        }
    }
}

@Composable
private fun RgbPill(label: String, value: Int) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = "$label:$value",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CompactQuickPresetsRow(
    onSelectPreset: (Color) -> Unit,
    onResetInitial: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Presets",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presets = listOf(
                Color(0xFFEF4444), // Vivid Red
                Color(0xFF3B82F6), // Royal Blue
                Color(0xFF10B981), // Emerald Green
                Color(0xFFF59E0B), // Warm Amber
                Color(0xFF8B5CF6), // Violet Purple
                Color(0xFFEC4899)  // Hot Pink
            )

            presets.forEach { preset ->
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(preset)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                        .clickable { onSelectPreset(preset) }
                )
            }

            // Restore initial color reset icon
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                    .clickable { onResetInitial() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Reset",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
