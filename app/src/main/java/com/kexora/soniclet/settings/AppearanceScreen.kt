package com.kexora.soniclet.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kexora.soniclet.player.AppTheme
import com.kexora.soniclet.tools.CoverArtEditorDialog
import com.kexora.soniclet.ui.components.common.AdvancedColorPickerDialog
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.theme.BackgroundPresets

/**
 * Clean, balanced, and modern Appearance Settings Screen.
 * Uses consistent grouped surfaces, clean capsule pills, and fluid controls.
 */
@Composable
fun AppearanceSettingsSubPage(
    appTheme: AppTheme = AppTheme.DARK,
    accentColor: Long,
    backgroundMode: String,
    customBackgroundUri: String? = null,
    bgImageBlur: Float = 10f,
    bgImageDim: Float = 0.5f,
    solidBgColor: Long = 0xFF121212L,
    solidBgOpacity: Float = 1.0f,
    gradientPresetIndex: Int = 0,
    customGradientColors: List<Long> = listOf(0xFF0F2027L, 0xFF203A43L, 0xFF2C5364L),
    gradientIntensity: Float = 1.0f,
    gradientBlur: Float = 0f,
    isKariModeEnabled: Boolean,
    kariBlur: Float = 12f,
    kariOpacity: Float = 0.9f,
    onSelectAppTheme: (AppTheme) -> Unit = {},
    onSelectAccentColor: (Long) -> Unit,
    onSelectBackgroundMode: (String) -> Unit,
    onSelectCustomBackgroundUri: (String?) -> Unit,
    onSetBgImageBlur: (Float) -> Unit,
    onSetBgImageDim: (Float) -> Unit,
    onSetSolidBgColor: (Long) -> Unit,
    onSetSolidBgOpacity: (Float) -> Unit,
    onSetGradientPresetIndex: (Int) -> Unit,
    onSetCustomGradientColors: (List<Long>) -> Unit,
    onSetGradientIntensity: (Float) -> Unit,
    onSetGradientBlur: (Float) -> Unit,
    onToggleKariMode: (Boolean) -> Unit,
    onSetKariBlur: (Float) -> Unit,
    onSetKariOpacity: (Float) -> Unit,
    onOpenHomeCustomizer: () -> Unit
) {
    var showColorDialog by remember { mutableStateOf(false) }
    var showCustomGradientDialog by remember { mutableStateOf(false) }
    var tempGradientColors by remember { mutableStateOf(customGradientColors) }
    var editingGradientIndex by remember { mutableIntStateOf(0) }
    var tempColorValue by remember { mutableLongStateOf(accentColor) }
    var colorPickerMode by remember { mutableStateOf("ACCENT") }

    var editingWallpaperUri by remember { mutableStateOf<Uri?>(null) }
    var showWallpaperEditor by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            editingWallpaperUri = uri
            showWallpaperEditor = true
        }
    }

    val actualBackgroundMode = if (isKariModeEnabled) "DYNAMIC" else backgroundMode
    val activeAccentColor = Color(accentColor)

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Theme Mode Section (Light / Dark / System)
        AppearanceSectionGroup(title = "THEME MODE") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val themeOptions = listOf(
                    AppTheme.LIGHT to ("Light" to Icons.Rounded.LightMode),
                    AppTheme.DARK to ("Dark" to Icons.Rounded.DarkMode),
                    AppTheme.SYSTEM to ("System" to Icons.Rounded.SettingsBrightness)
                )
                themeOptions.forEach { (theme, pair) ->
                    val (label, icon) = pair
                    val isSelected = appTheme == theme
                    Surface(
                        onClick = { onSelectAppTheme(theme) },
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
        // 1. Accent Color Section
        AppearanceSectionGroup(title = "ACCENT COLOR") {
            val isDynamic = actualBackgroundMode == "DYNAMIC"

            if (isDynamic) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(activeAccentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = activeAccentColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Dynamic Mode active. Accents automatically harmonize with the active album artwork.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val colors = listOf(
                        0xFF00E676L, // Emerald Soniclet
                        0xFF00E5FFL, // Electric Cyan
                        0xFFAA00FFL, // Mystic Violet
                        0xFFFF3D00L, // Sunset Orange
                        0xFFFFD600L, // Cyber Gold
                        0xFFFF4081L, // Neon Pink
                        0xFF3D5AFEL, // Royal Indigo
                        0xFF00B0FFL, // Sky Blue
                        0xFFE040FBL, // Orchid Glow
                        0xFFFFFFFFL  // Pure Minimal
                    )

                    colors.forEach { colorValue ->
                        val isSelected = accentColor == colorValue
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(colorValue))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onSelectAccentColor(colorValue) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = if (colorValue == 0xFFFFFFFFL || colorValue == 0xFFFFD600L) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Custom Color Wheel Picker Tile
                    Surface(
                        onClick = {
                            tempColorValue = accentColor
                            colorPickerMode = "ACCENT"
                            showColorDialog = true
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Colorize,
                                contentDescription = "Custom Hex Color",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Background Canvas Style Section
        AppearanceSectionGroup(title = "BACKGROUND CANVAS") {
            val bgModes = listOf(
                "DYNAMIC" to "Dynamic Glow",
                "GRADIENT" to "Gradient",
                "SOLID" to "Solid Tone",
                "CUSTOM" to "Wallpaper",
                "OFF" to "Default"
            )

            // Horizontal Pill Mode Switchers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bgModes.forEach { (mode, label) ->
                    val isSelected = actualBackgroundMode == mode
                    Surface(
                        onClick = {
                            if (mode == "DYNAMIC") {
                                onToggleKariMode(true)
                            } else {
                                onToggleKariMode(false)
                                onSelectBackgroundMode(mode)
                                if (mode == "CUSTOM" && customBackgroundUri.isNullOrBlank()) {
                                    visualMediaLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            }
                        },
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = actualBackgroundMode,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                label = "bg_mode_anim"
            ) { mode ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    when (mode) {
                        "DYNAMIC" -> {
                            CompactSliderControl(
                                label = "Blur Radius",
                                value = kariBlur,
                                displayValue = "${kariBlur.toInt()} dp",
                                valueRange = 4f..60f,
                                onValueChange = onSetKariBlur
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            CompactSliderControl(
                                label = "Glow Opacity",
                                value = kariOpacity,
                                displayValue = "${(kariOpacity * 100).toInt()}%",
                                valueRange = 0.2f..1.0f,
                                onValueChange = onSetKariOpacity
                            )
                        }

                        "SOLID" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val darkSolids = listOf(
                                    0xFF000000L to "OLED Black",
                                    0xFF0A0E17L to "Midnight",
                                    0xFF0F1A1CL to "Emerald",
                                    0xFF140D1EL to "Velvet",
                                    0xFF18181BL to "Slate"
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    darkSolids.forEach { (colorVal, name) ->
                                        val isSelected = solidBgColor == colorVal
                                        Surface(
                                            onClick = { onSetSolidBgColor(colorVal) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(colorVal),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = name,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }

                                    // Pick Custom Solid Color
                                    Surface(
                                        onClick = {
                                            tempColorValue = solidBgColor
                                            colorPickerMode = "SOLID"
                                            showColorDialog = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.Colorize, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Custom", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                            }
                        }

                        "GRADIENT" -> {
                            val isCustomGradientSelected = gradientPresetIndex == 8
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                border = if (isCustomGradientSelected) BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clickable { onSetGradientPresetIndex(8) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.horizontalGradient(customGradientColors.map { Color(it) }))
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Custom 3-Color Gradient",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        IconButton(
                                            onClick = {
                                                tempGradientColors = customGradientColors
                                                showCustomGradientDialog = true
                                            },
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.4f))
                                        ) {
                                            Icon(
                                                Icons.Rounded.Edit,
                                                contentDescription = "Edit",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Presets in 2 columns
                            BackgroundPresets.GRADIENT_PRESETS.chunked(2).forEach { rowPresets ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowPresets.forEach { preset ->
                                        val isSelected = gradientPresetIndex == preset.id
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            border = if (isSelected) BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary) else null,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(42.dp)
                                                .clickable { onSetGradientPresetIndex(preset.id) }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Brush.horizontalGradient(preset.colors))
                                                    .padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        preset.name,
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Rounded.CheckCircle,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        "CUSTOM" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (customBackgroundUri != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = customBackgroundUri,
                                            contentDescription = "Wallpaper",
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    editingWallpaperUri = Uri.parse(customBackgroundUri)
                                                    showWallpaperEditor = true
                                                },
                                                shape = CircleShape,
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 4.dp)
                                            ) {
                                                Text("Edit", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    visualMediaLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                shape = CircleShape,
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 4.dp)
                                            ) {
                                                Text("Change", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                            }

                                            IconButton(
                                                onClick = {
                                                    onSelectCustomBackgroundUri(null)
                                                    onSelectBackgroundMode("DYNAMIC")
                                                    Toast.makeText(context, "Wallpaper removed", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Remove",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            visualMediaLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = CircleShape,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Select Photo Wallpaper", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                }

                                CompactSliderControl(
                                    label = "Background Blur",
                                    value = bgImageBlur,
                                    displayValue = "${bgImageBlur.toInt()} dp",
                                    valueRange = 0f..40f,
                                    onValueChange = onSetBgImageBlur
                                )
                                CompactSliderControl(
                                    label = "Dimming Overlay",
                                    value = bgImageDim,
                                    displayValue = "${(bgImageDim * 100).toInt()}%",
                                    valueRange = 0f..0.9f,
                                    onValueChange = onSetBgImageDim
                                )
                            }
                        }

                        "OFF" -> {
                            Text(
                                "Standard minimalist background with zero canvas blur.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. Library & Navigation Section
        AppearanceSectionGroup(title = "HOME & NAVIGATION") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenHomeCustomizer() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.ViewCarousel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Customize Home Tabs & Sections",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    )
                    Text(
                        "Reorder, show, or hide home & library sections",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        MadeInIndiaFooter()
        Spacer(modifier = Modifier.height(com.kexora.soniclet.LocalPlayerBottomPadding.current + 8.dp))
    }

    // Color Dialogs
    if (showColorDialog) {
        AdvancedColorPickerDialog(
            initialColor = Color(tempColorValue),
            onDismiss = { showColorDialog = false },
            onColorSelected = { newColor ->
                val newColorLong = newColor.toArgb().toLong() and 0xFFFFFFFFL
                when (colorPickerMode) {
                    "ACCENT" -> onSelectAccentColor(newColorLong)
                    "SOLID" -> onSetSolidBgColor(newColorLong)
                    "GRADIENT" -> {
                        val newColors = tempGradientColors.toMutableList()
                        newColors[editingGradientIndex] = newColorLong
                        tempGradientColors = newColors
                    }
                }
                showColorDialog = false
            }
        )
    }

    if (showCustomGradientDialog) {
        AlertDialog(
            onDismissRequest = { showCustomGradientDialog = false },
            title = { Text("Custom 3-Color Gradient", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "Tap any circle to customize stop color:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        tempGradientColors.forEachIndexed { index, colorValue ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorValue))
                                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                    .clickable {
                                        editingGradientIndex = index
                                        tempColorValue = colorValue
                                        colorPickerMode = "GRADIENT"
                                        showColorDialog = true
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Live Blend Preview", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(tempGradientColors.map { Color(it) }))
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetCustomGradientColors(tempGradientColors)
                        onSetGradientPresetIndex(8)
                        showCustomGradientDialog = false
                    },
                    shape = CircleShape
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomGradientDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showWallpaperEditor && editingWallpaperUri != null) {
        CoverArtEditorDialog(
            imageUri = editingWallpaperUri!!,
            title = "Edit Wallpaper",
            isWallpaperMode = true,
            onDismissRequest = { showWallpaperEditor = false },
            onApplyEditedArt = { editedUri ->
                onSelectCustomBackgroundUri(editedUri.toString())
                onSelectBackgroundMode("CUSTOM")
                showWallpaperEditor = false
                Toast.makeText(context, "Wallpaper updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * Reusable clean grouped container for appearance settings
 */
@Composable
private fun AppearanceSectionGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                content = content
            )
        }
    }
}

/**
 * Reusable compact slider control
 */
@Composable
private fun CompactSliderControl(
    label: String,
    value: Float,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
