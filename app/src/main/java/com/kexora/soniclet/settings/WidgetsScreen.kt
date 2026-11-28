package com.kexora.soniclet.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.utils.CompatBlurTransformation
import com.kexora.soniclet.utils.SmartTitleCleaner
import com.kexora.soniclet.widget.SonicletWidgetManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class WidgetPresetInfo(
    val id: String,
    val name: String,
    val sizeLabel: String
)

private data class WidgetStyleOption(
    val key: String,
    val label: String
)

private data class WidgetAccentSwatch(
    val label: String,
    val colorLong: Long? // null means follow current app accent color
)

private val WIDGET_PRESETS = listOf(
    WidgetPresetInfo("tiny", "Tiny", "1×1"),
    WidgetPresetInfo("small_bar", "Small Bar", "4×1"),
    WidgetPresetInfo("compact", "Compact", "4×1"),
    WidgetPresetInfo("square", "Square", "2×2"),
    WidgetPresetInfo("vinyl", "Vinyl", "2×2"),
    WidgetPresetInfo("card", "Card", "4×2"),
    WidgetPresetInfo("pro", "Pro", "4×3")
)

private val WIDGET_STYLES = listOf(
    WidgetStyleOption("AMOLED", "Pure Black"),
    WidgetStyleOption("GLASS", "Dark Glass"),
    WidgetStyleOption("LIGHT_GLASS", "Light Glass"),
    WidgetStyleOption("ALBUM_COLOR", "Album Color")
)

private val WIDGET_ACCENT_SWATCHES = listOf(
    WidgetAccentSwatch("App Color", null),
    WidgetAccentSwatch("Emerald", 0xFF00E676L),
    WidgetAccentSwatch("Cyan", 0xFF00D8FFL),
    WidgetAccentSwatch("Purple", 0xFF8B5CF6L),
    WidgetAccentSwatch("Orange", 0xFFFF6D00L),
    WidgetAccentSwatch("Yellow", 0xFFFFD600L),
    WidgetAccentSwatch("White", 0xFFFFFFFFL)
)

/**
 * Clean, box-free Home Screen Widget Customizer.
 * Features accurate live previews for all 7 widgets, everyday English labels,
 * custom sleek pill sliders, and instant home-screen widget sync.
 */
@Composable
fun WidgetSettingsSubPage(
    currentSong: Song?,
    isPlaying: Boolean,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appPrimary = MaterialTheme.colorScheme.primary
    val appPrimaryLong = remember(appPrimary) { appPrimary.toArgb().toLong() and 0xFFFFFFFFL }

    val prefs = remember { SonicletWidgetManager.getPrefs(context) }

    var selectedStyle by remember {
        val saved = prefs.getString(SonicletWidgetManager.KEY_THEME, "AMOLED") ?: "AMOLED"
        val normalized = when (saved) {
            "AMOLED", "GLASS", "LIGHT_GLASS", "ALBUM_COLOR" -> saved
            "FROSTED" -> "GLASS"
            "ACCENT", "GRADIENT" -> "ALBUM_COLOR"
            else -> "AMOLED"
        }
        mutableStateOf(normalized)
    }

    var selectedAccentHex by remember {
        val saved = try {
            prefs.getLong(SonicletWidgetManager.KEY_ACCENT_COLOR, appPrimaryLong)
        } catch (_: Exception) {
            appPrimaryLong
        }
        mutableLongStateOf(saved)
    }

    var useAppAccent by remember {
        mutableStateOf(WIDGET_ACCENT_SWATCHES.drop(1).none { it.colorLong == selectedAccentHex })
    }

    var cornerRoundness by remember {
        mutableFloatStateOf(prefs.getFloat(SonicletWidgetManager.KEY_CORNER_RADIUS_FLOAT, 0.55f).coerceIn(0f, 1f))
    }

    var backgroundDarkness by remember {
        mutableFloatStateOf(prefs.getFloat(SonicletWidgetManager.KEY_OPACITY, 0.85f).coerceIn(0.20f, 1f))
    }

    var showAlbumPhotoBg by remember {
        mutableStateOf(prefs.getBoolean(SonicletWidgetManager.KEY_BG_COVER_ART, true))
    }

    var showProgressBar by remember {
        mutableStateOf(prefs.getBoolean(SonicletWidgetManager.KEY_SHOW_PROGRESS, true))
    }

    var showFavoriteHeart by remember {
        mutableStateOf(prefs.getBoolean(SonicletWidgetManager.KEY_SHOW_FAVORITE, true))
    }

    var showQualityTag by remember {
        mutableStateOf(prefs.getBoolean(SonicletWidgetManager.KEY_SHOW_QUALITY_TAG, true))
    }

    val effectiveAccentColor = if (useAppAccent) appPrimary else Color(selectedAccentHex.toInt())
    val effectiveAccentLong = if (useAppAccent) appPrimaryLong else selectedAccentHex

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { WIDGET_PRESETS.size })
    val activePreset = WIDGET_PRESETS[pagerState.currentPage]

    fun persistAndRefresh() {
        SonicletWidgetManager.saveCustomization(
            context = context,
            theme = selectedStyle,
            cornerRadiusFloat = cornerRoundness,
            opacity = backgroundDarkness,
            accentColor = effectiveAccentLong,
            bgCoverArt = showAlbumPhotoBg,
            showProgress = showProgressBar,
            showFavorite = showFavoriteHeart,
            showQualityTag = showQualityTag,
            dynamicTheme = selectedStyle == "ALBUM_COLOR"
        )
    }

    val songTitle = remember(currentSong?.title, currentSong?.filePath) {
        currentSong?.let { SmartTitleCleaner.cleanTitle(it.title, it.filePath) } ?: "Midnight Horizon"
    }
    val songArtist = remember(currentSong?.artist) {
        val raw = currentSong?.artist
        if (raw == null || SmartTitleCleaner.isPlaceholderArtist(raw)) "Soniclet Audio" else raw.trim()
    }
    val qualityLabel = remember(currentSong?.filePath) {
        val p = (currentSong?.filePath ?: "").lowercase()
        when {
            p.endsWith(".flac") || p.endsWith(".wav") || p.endsWith(".alac") -> "HI-RES"
            p.endsWith(".m4a") || p.endsWith(".aac") -> "M4A"
            else -> "MP3"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("widgets_settings_screen")
    ) {
        // 1. Compact Borderless Preview Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "LIVE PREVIEW",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.0.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            // Sleek Pill Selector (< Tiny 1×1 • 1/7 >)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                val prev = (pagerState.currentPage - 1 + WIDGET_PRESETS.size) % WIDGET_PRESETS.size
                                scope.launch { pagerState.animateScrollToPage(prev) }
                            }
                            .testTag("widget_preview_prev"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Previous widget style",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "${activePreset.name} ${activePreset.sizeLabel} • ${pagerState.currentPage + 1}/${WIDGET_PRESETS.size}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                val next = (pagerState.currentPage + 1) % WIDGET_PRESETS.size
                                scope.launch { pagerState.animateScrollToPage(next) }
                            }
                            .testTag("widget_preview_next"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Next widget style",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Borderless Preview Stage with Subtle Grid-Dot Wallpaper Texture
        val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(212.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 18.dp.toPx()
                val dotRadius = 1.dp.toPx()
                var y = step / 2f
                while (y < size.height) {
                    var x = step / 2f
                    while (x < size.width) {
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    WidgetLivePreviewItem(
                        presetIndex = page,
                        songTitle = songTitle,
                        songArtist = songArtist,
                        albumArtUri = currentSong?.albumArtUri?.toString(),
                        isPlaying = isPlaying,
                        styleKey = selectedStyle,
                        accentColor = effectiveAccentColor,
                        cornerRoundness = cornerRoundness,
                        darkness = backgroundDarkness,
                        showAlbumPhotoBg = showAlbumPhotoBg,
                        showProgressBar = showProgressBar,
                        showFavoriteHeart = showFavoriteHeart,
                        showQualityTag = showQualityTag,
                        qualityLabel = qualityLabel
                    )
                }
            }
        }

        // Smooth Capsule Pager Dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(WIDGET_PRESETS.size) { index ->
                val isSelected = pagerState.currentPage == index
                val dotWidth by animateDpAsState(
                    targetValue = if (isSelected) 18.dp else 6.dp,
                    animationSpec = tween(220),
                    label = "widget_dot_width"
                )
                val dotBg by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                    },
                    animationSpec = tween(220),
                    label = "widget_dot_color"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(dotBg)
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                )
            }
        }

        // 3. STYLE & COLOR
        EdgeSectionHeader(title = "STYLE & COLOR")

        // Style Pills LazyRow
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(WIDGET_STYLES) { style ->
                val isSelected = selectedStyle == style.key
                val bg by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    animationSpec = tween(180),
                    label = "widget_style_bg"
                )
                val fg by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    animationSpec = tween(180),
                    label = "widget_style_fg"
                )

                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .clickable {
                            selectedStyle = style.key
                            persistAndRefresh()
                        }
                        .padding(horizontal = 16.dp)
                        .testTag("widget_style_${style.key.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        color = fg
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Button Accent Swatches LazyRow
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(WIDGET_ACCENT_SWATCHES) { swatch ->
                val swatchColor = swatch.colorLong?.let { Color(it.toInt()) } ?: appPrimary
                val isSelected = if (swatch.colorLong == null) {
                    useAppAccent
                } else {
                    !useAppAccent && selectedAccentHex == swatch.colorLong
                }
                val checkTint = if (swatchColor.luminance() > 0.45f) Color(0xFF0A0F0D) else Color.White

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            if (swatch.colorLong == null) {
                                useAppAccent = true
                                selectedAccentHex = appPrimaryLong
                            } else {
                                useAppAccent = false
                                selectedAccentHex = swatch.colorLong
                            }
                            persistAndRefresh()
                        }
                        .testTag("widget_color_${swatch.label.lowercase().replace(" ", "_")}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier.border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                }
                            )
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(swatchColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "${swatch.label} selected",
                                tint = checkTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = swatch.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

        // 4. SHAPE & TRANSPARENCY (Custom Smooth Thin Pill Sliders)
        EdgeSectionHeader(title = "SHAPE & TRANSPARENCY")

        WidgetPillSliderRow(
            label = "Corner Roundness",
            valueText = "${(cornerRoundness * 100f).roundToInt()}%",
            value = cornerRoundness,
            valueRange = 0f..1f,
            onValueChange = { cornerRoundness = it },
            onValueChangeFinished = { persistAndRefresh() },
            testTag = "widget_corner_roundness_slider"
        )

        Spacer(modifier = Modifier.height(6.dp))

        WidgetPillSliderRow(
            label = "Background Darkness",
            valueText = "${(backgroundDarkness * 100f).roundToInt()}%",
            value = backgroundDarkness,
            valueRange = 0.20f..1f,
            onValueChange = { backgroundDarkness = it },
            onValueChangeFinished = { persistAndRefresh() },
            testTag = "widget_darkness_slider"
        )

        Spacer(modifier = Modifier.height(10.dp))
        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

        // 5. WIDGET OPTIONS (Flat Edge-to-Edge Toggles)
        EdgeSectionHeader(title = "WIDGET OPTIONS")

        WidgetOptionToggleRow(
            title = "Show Album Photo in Background",
            subtitle = "Use the current song's cover art softly behind the widget",
            checked = showAlbumPhotoBg,
            onCheckedChange = {
                showAlbumPhotoBg = it
                persistAndRefresh()
            },
            testTag = "widget_toggle_album_bg"
        )
        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

        WidgetOptionToggleRow(
            title = "Show Song Progress Bar",
            subtitle = "Display how much of the song has played",
            checked = showProgressBar,
            onCheckedChange = {
                showProgressBar = it
                persistAndRefresh()
            },
            testTag = "widget_toggle_progress"
        )
        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

        WidgetOptionToggleRow(
            title = "Show Like Heart Button",
            subtitle = "Quickly like or unlike songs right from your home screen",
            checked = showFavoriteHeart,
            onCheckedChange = {
                showFavoriteHeart = it
                persistAndRefresh()
            },
            testTag = "widget_toggle_favorite"
        )
        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

        WidgetOptionToggleRow(
            title = "Show Sound Quality Tag",
            subtitle = "Show a small audio tag like HI-RES or MP3 on larger widgets",
            checked = showQualityTag,
            onCheckedChange = {
                showQualityTag = it
                persistAndRefresh()
            },
            testTag = "widget_toggle_quality"
        )

        Spacer(modifier = Modifier.height(22.dp))

        // 6. Full-Pill Primary CTA
        Button(
            onClick = {
                persistAndRefresh()
                val pinned = SonicletWidgetManager.requestPinWidget(context, pagerState.currentPage)
                if (pinned) {
                    Toast.makeText(
                        context,
                        "Choose where to place ${activePreset.name} on your home screen",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Long-press your home screen and open Widgets to add Soniclet",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp)
                .testTag("widget_add_to_home_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add Widget to Home Screen",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        MadeInIndiaFooter()
        EdgeSettingsBottomSpacer()
    }
}

@Composable
private fun WidgetPillSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    testTag: String
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val normalized = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    fun mapXToValue(x: Float): Float {
        val frac = (x / barWidthPx).coerceIn(0f, 1f)
        return valueRange.start + frac * (valueRange.endInclusive - valueRange.start)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = activeColor
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        onValueChange(mapXToValue(offset.x))
                        onValueChangeFinished()
                    }
                }
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onValueChangeFinished() },
                        onDragCancel = { onValueChangeFinished() },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            onValueChange(mapXToValue(change.position.x))
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            ) {
                val trackHeight = 6.dp.toPx()
                val trackTop = (size.height - trackHeight) / 2f
                val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                val thumbRadius = 9.dp.toPx()
                val usableWidth = (size.width - thumbRadius * 2f).coerceAtLeast(1f)
                val thumbX = thumbRadius + usableWidth * normalized

                // Track background
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackHeight),
                    cornerRadius = corner
                )
                // Active fill
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, trackTop),
                    size = Size(thumbX.coerceAtLeast(trackHeight), trackHeight),
                    cornerRadius = corner
                )
                // Soft glow halo
                drawCircle(
                    color = activeColor.copy(alpha = 0.25f),
                    radius = thumbRadius + 4.dp.toPx(),
                    center = Offset(thumbX, size.height / 2f)
                )
                // Crisp thumb
                drawCircle(
                    color = activeColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, size.height / 2f)
                )
            }
        }
    }
}

@Composable
private fun WidgetOptionToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = sonicletSwitchColors()
        )
    }
}

/**
 * Distinct, accurate live previews matching all 7 real Home Screen widget layouts.
 */
@Composable
private fun WidgetLivePreviewItem(
    presetIndex: Int,
    songTitle: String,
    songArtist: String,
    albumArtUri: String?,
    isPlaying: Boolean,
    styleKey: String,
    accentColor: Color,
    cornerRoundness: Float,
    darkness: Float,
    showAlbumPhotoBg: Boolean,
    showProgressBar: Boolean,
    showFavoriteHeart: Boolean,
    showQualityTag: Boolean,
    qualityLabel: String
) {
    val isLight = styleKey == "LIGHT_GLASS" && !(showAlbumPhotoBg && albumArtUri != null)
    val fgPrimary = if (isLight) Color(0xFF141917) else Color.White
    val fgSecondary = if (isLight) Color(0xFF555E5A) else Color.White.copy(alpha = 0.72f)
    val playIconTint = if (accentColor.luminance() > 0.42f) Color(0xFF0A0F0D) else Color.White
    val borderColor = if (isLight) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.14f)

    when (presetIndex) {
        // 0: Tiny 1x1 — Full-bleed rounded album cover + soft scrim + centered Play/Pause, ZERO cramped text
        0 -> {
            val radius = (10f + cornerRoundness * 36f).dp
            WidgetSurfaceFrame(
                width = 104.dp,
                height = 104.dp,
                cornerRadius = radius,
                styleKey = styleKey,
                accentColor = accentColor,
                darkness = darkness,
                albumArtUri = albumArtUri,
                showAlbumPhotoBg = true,
                blurArt = false,
                borderColor = borderColor
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    WidgetPreviewPlayButton(
                        size = 44.dp,
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        iconTint = playIconTint,
                        roundness = cornerRoundness
                    )
                }
            }
        }

        // 1: Small Bar 4x1
        1 -> {
            val radius = (10f + cornerRoundness * 26f).dp
            WidgetSurfaceFrame(
                width = 304.dp,
                height = 68.dp,
                cornerRadius = radius,
                styleKey = styleKey,
                accentColor = accentColor,
                darkness = darkness,
                albumArtUri = albumArtUri,
                showAlbumPhotoBg = showAlbumPhotoBg,
                blurArt = true,
                borderColor = borderColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WidgetPreviewArtBox(albumArtUri = albumArtUri, size = 44.dp, radius = 12.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songTitle,
                            color = fgPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = songArtist,
                            color = fgSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    WidgetPreviewPlayButton(
                        size = 36.dp,
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        iconTint = playIconTint,
                        roundness = cornerRoundness
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }

        // 2: Compact 4x1
        2 -> {
            val radius = (10f + cornerRoundness * 22f).dp
            WidgetSurfaceFrame(
                width = 312.dp,
                height = 84.dp,
                cornerRadius = radius,
                styleKey = styleKey,
                accentColor = accentColor,
                darkness = darkness,
                albumArtUri = albumArtUri,
                showAlbumPhotoBg = showAlbumPhotoBg,
                blurArt = true,
                borderColor = borderColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WidgetPreviewArtBox(albumArtUri = albumArtUri, size = 54.dp, radius = 14.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songTitle,
                            color = fgPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = songArtist,
                            color = fgSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showProgressBar) {
                            Spacer(modifier = Modifier.height(6.dp))
                            WidgetPreviewProgressTrack(accentColor = accentColor, fraction = 0.42f, lightMode = isLight)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (showFavoriteHeart) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    WidgetPreviewPlayButton(
                        size = 38.dp,
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        iconTint = playIconTint,
                        roundness = cornerRoundness
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }

        // 3: Square 2x2
        3 -> {
            val radius = (12f + cornerRoundness * 26f).dp
            WidgetSurfaceFrame(
                width = 166.dp,
                height = 166.dp,
                cornerRadius = radius,
                styleKey = styleKey,
                accentColor = accentColor,
                darkness = darkness,
                albumArtUri = albumArtUri,
                showAlbumPhotoBg = showAlbumPhotoBg,
                blurArt = true,
                borderColor = borderColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    WidgetPreviewArtBox(albumArtUri = albumArtUri, size = 56.dp, radius = 14.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = songTitle,
                            color = fgPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = songArtist,
                            color = fgSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(22.dp))
                        WidgetPreviewPlayButton(
                            size = 36.dp,
                            isPlaying = isPlaying,
                            accentColor = accentColor,
                            iconTint = playIconTint,
                            roundness = cornerRoundness
                        )
                        Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        // 4: Vinyl 2x2
        4 -> {
            val radius = (12f + cornerRoundness * 26f).dp
            WidgetSurfaceFrame(
                width = 176.dp,
                height = 176.dp,
                cornerRadius = radius,
                styleKey = styleKey,
                accentColor = accentColor,
                darkness = darkness,
                albumArtUri = albumArtUri,
                showAlbumPhotoBg = showAlbumPhotoBg,
                blurArt = true,
                borderColor = borderColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Circular Vinyl Disc
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141615))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        WidgetPreviewArtBox(albumArtUri = albumArtUri, size = 48.dp, radius = 24.dp)
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0A0D0C))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        )
                    }

                    Text(
                        text = songTitle,
                        color = fgPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(20.dp))
                        WidgetPreviewPlayButton(
                            size = 36.dp,
                            isPlaying = isPlaying,
                            accentColor = accentColor,
                            iconTint = playIconTint,
                            roundness = cornerRoundness
                        )
                        Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(20.dp))
                        if (showFavoriteHeart) {
                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 5: Card 4x2
        5 -> {
            val radius = (12f + cornerRoundness * 22f).dp
            WidgetSurfaceFrame(
                width = 312.dp,
                height = 148.dp,
                cornerRadius = radius,
                styleKey = styleKey,
                accentColor = accentColor,
                darkness = darkness,
                albumArtUri = albumArtUri,
                showAlbumPhotoBg = showAlbumPhotoBg,
                blurArt = true,
                borderColor = borderColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WidgetPreviewArtBox(albumArtUri = albumArtUri, size = 56.dp, radius = 14.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = songTitle,
                                color = fgPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = songArtist,
                                color = fgSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (showFavoriteHeart) {
                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                    }

                    if (showProgressBar) {
                        WidgetPreviewProgressTrack(accentColor = accentColor, fraction = 0.56f, lightMode = isLight)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = fgSecondary, modifier = Modifier.size(18.dp))
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(24.dp))
                        WidgetPreviewPlayButton(
                            size = 42.dp,
                            isPlaying = isPlaying,
                            accentColor = accentColor,
                            iconTint = playIconTint,
                            roundness = cornerRoundness
                        )
                        Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(24.dp))
                        Icon(
                            if (showFavoriteHeart) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = if (showFavoriteHeart) accentColor else fgSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 6: Pro 4x3
        else -> {
            val radius = (12f + cornerRoundness * 20f).dp
            WidgetSurfaceFrame(
                width = 312.dp,
                height = 190.dp,
                cornerRadius = radius,
                styleKey = styleKey,
                accentColor = accentColor,
                darkness = darkness,
                albumArtUri = albumArtUri,
                showAlbumPhotoBg = showAlbumPhotoBg,
                blurArt = true,
                borderColor = borderColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SONICLET",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                            if (showQualityTag) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(fgPrimary.copy(alpha = 0.14f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = qualityLabel,
                                        color = fgPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (showFavoriteHeart) {
                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WidgetPreviewArtBox(albumArtUri = albumArtUri, size = 62.dp, radius = 16.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = songTitle,
                                color = fgPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = songArtist,
                                color = fgSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (showProgressBar) {
                        Column {
                            WidgetPreviewProgressTrack(accentColor = accentColor, fraction = 0.48f, lightMode = isLight)
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("01:28", color = fgSecondary, fontSize = 10.sp)
                                Text("03:42", color = fgSecondary, fontSize = 10.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(25.dp))
                        WidgetPreviewPlayButton(
                            size = 44.dp,
                            isPlaying = isPlaying,
                            accentColor = accentColor,
                            iconTint = playIconTint,
                            roundness = cornerRoundness
                        )
                        Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = fgPrimary, modifier = Modifier.size(25.dp))
                        Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = fgSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetSurfaceFrame(
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    styleKey: String,
    accentColor: Color,
    darkness: Float,
    albumArtUri: String?,
    showAlbumPhotoBg: Boolean,
    blurArt: Boolean,
    borderColor: Color,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(cornerRadius)
    val bgBrush = remember(styleKey, accentColor, darkness) {
        when (styleKey) {
            "LIGHT_GLASS" -> Brush.verticalGradient(
                listOf(
                    Color(0xFFF6F8F7).copy(alpha = (darkness * 0.96f).coerceIn(0.35f, 0.96f)),
                    Color(0xFFE6ECE9).copy(alpha = (darkness * 0.96f).coerceIn(0.35f, 0.96f))
                )
            )
            "GLASS" -> Brush.verticalGradient(
                listOf(
                    Color(0xFF1A221F).copy(alpha = (darkness * 0.88f).coerceIn(0.28f, 0.92f)),
                    Color(0xFF0E1311).copy(alpha = (darkness * 0.92f).coerceIn(0.32f, 0.95f))
                )
            )
            "ALBUM_COLOR" -> Brush.linearGradient(
                listOf(
                    accentColor.copy(alpha = (darkness * 0.36f).coerceIn(0.18f, 0.45f)),
                    Color(0xFF0B110E).copy(alpha = darkness.coerceIn(0.55f, 0.96f))
                )
            )
            else -> Brush.verticalGradient(
                listOf(
                    Color(0xFF070A09).copy(alpha = darkness.coerceIn(0.25f, 1f)),
                    Color(0xFF070A09).copy(alpha = darkness.coerceIn(0.25f, 1f))
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(bgBrush)
            .border(0.8.dp, borderColor, shape)
    ) {
        if (showAlbumPhotoBg && albumArtUri != null) {
            val request = remember(albumArtUri, blurArt, context) {
                ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .crossfade(250)
                    .apply {
                        if (blurArt && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            transformations(CompatBlurTransformation(context, radius = 20, sampling = 4f))
                        }
                    }
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (blurArt && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(18.dp)
                        } else {
                            Modifier
                        }
                    )
            )
            if (blurArt) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color(0xFF070A09).copy(alpha = (0.38f + darkness * 0.46f).coerceIn(0.40f, 0.88f))
                        )
                )
            }
        }
        content()
    }
}

@Composable
private fun WidgetPreviewArtBox(
    albumArtUri: String?,
    size: Dp,
    radius: Dp
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Color(0xFF1A221F)),
        contentAlignment = Alignment.Center
    ) {
        if (albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .crossfade(200)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(size * 0.48f)
            )
        }
    }
}

@Composable
private fun WidgetPreviewPlayButton(
    size: Dp,
    isPlaying: Boolean,
    accentColor: Color,
    iconTint: Color,
    roundness: Float
) {
    val corner = (size.value * (0.22f + roundness * 0.28f)).dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(accentColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

@Composable
private fun WidgetPreviewProgressTrack(
    accentColor: Color,
    fraction: Float,
    lightMode: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(if (lightMode) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(accentColor)
        )
    }
}
