package com.kexora.soniclet.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.widget.SonicletWidgetManager

/**
 * Modern, Fluid & Clean Home Screen Widgets Customizer Screen.
 * 8.dp Universal Side Margin & Translucent Canvas.
 */
@Composable
fun WidgetSettingsSubPage(
    currentSong: Song?,
    isPlaying: Boolean,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { SonicletWidgetManager.getPrefs(context) }

    val pagerState = rememberPagerState(pageCount = { 7 })
    val widgetTypes = listOf("Tiny Square (1x1)", "Mini Bar (2x1)", "Compact Bar (4x1)", "Square (2x2)", "Card (4x2)", "Vinyl Spin (3x3)", "Pro Deck (4x4)")

    var widgetTheme by remember { mutableStateOf(try { prefs.getString(SonicletWidgetManager.KEY_THEME, "AMOLED") ?: "AMOLED" } catch (e: Exception) { "AMOLED" }) }
    var widgetOpacity by remember { mutableFloatStateOf(try { prefs.getFloat(SonicletWidgetManager.KEY_OPACITY, 0.95f) } catch (e: Exception) { 0.95f }) }
    var widgetBlur by remember { mutableFloatStateOf(try { prefs.getFloat(SonicletWidgetManager.KEY_BLUR, 0.5f) } catch (e: Exception) { 0.5f }) }
    var widgetCornerRadius by remember { mutableFloatStateOf(try { prefs.getFloat(SonicletWidgetManager.KEY_CORNER_RADIUS_FLOAT, 1.0f) } catch (e: Exception) { 1.0f }) }
    var widgetAccentColor by remember { mutableStateOf(try { prefs.getLong(SonicletWidgetManager.KEY_ACCENT_COLOR, 0xFF00E676) } catch (e: Exception) { try { prefs.getInt(SonicletWidgetManager.KEY_ACCENT_COLOR, 0xFF00E676.toInt()).toLong() } catch (e2: Exception) { 0xFF00E676 } }) }
    var bgCoverArt by remember { mutableStateOf(try { prefs.getBoolean(SonicletWidgetManager.KEY_BG_COVER_ART, true) } catch (e: Exception) { true }) }
    var hideArtThumbnail by remember { mutableStateOf(try { prefs.getBoolean(SonicletWidgetManager.KEY_HIDE_ART_THUMBNAIL, true) } catch (e: Exception) { true }) }
    var showFavorite by remember { mutableStateOf(try { prefs.getBoolean(SonicletWidgetManager.KEY_SHOW_FAVORITE, true) } catch (e: Exception) { true }) }
    var showSecondary by remember { mutableStateOf(try { prefs.getBoolean(SonicletWidgetManager.KEY_SHOW_SECONDARY, true) } catch (e: Exception) { true }) }
    var showProgress by remember { mutableStateOf(try { prefs.getBoolean(SonicletWidgetManager.KEY_SHOW_PROGRESS, true) } catch (e: Exception) { true }) }
    var showSeekButtons by remember { mutableStateOf(try { prefs.getBoolean(SonicletWidgetManager.KEY_SHOW_SEEK_BUTTONS, true) } catch (e: Exception) { true }) }
    var showPrevNext by remember { mutableStateOf(try { prefs.getBoolean(SonicletWidgetManager.KEY_SHOW_PREV_NEXT, true) } catch (e: Exception) { true }) }
    var showShuffleRepeat by remember { mutableStateOf(try { prefs.getBoolean(SonicletWidgetManager.KEY_SHOW_SHUFFLE_REPEAT, true) } catch (e: Exception) { true }) }

    val accentColorCompose = Color(widgetAccentColor.toInt())
    val songTitle = currentSong?.title ?: "Soniclet Studio"
    val songArtist = currentSong?.artist ?: "High Fidelity Audio"
    val songAlbum = currentSong?.album ?: "Lossless Edition"
    val songArtUri = currentSong?.albumArtUri

    fun saveAndSync() {
        SonicletWidgetManager.saveCustomization(
            context = context,
            theme = widgetTheme,
            dynamicTheme = prefs.getBoolean(SonicletWidgetManager.KEY_DYNAMIC_THEME, false),
            cornerRadiusFloat = widgetCornerRadius,
            opacity = widgetOpacity,
            blur = widgetBlur,
            showFavorite = showFavorite,
            showSecondary = showSecondary,
            showProgress = showProgress,
            accentColor = widgetAccentColor,
            bgCoverArt = bgCoverArt,
            hideArtThumbnail = hideArtThumbnail,
            showSeekButtons = showSeekButtons,
            showPrevNext = showPrevNext,
            showShuffleRepeat = showShuffleRepeat
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Top 32%: Live Preview Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f)
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
        ) {
            // Translucent Backdrop
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(currentSong?.albumArtUri ?: "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=600&auto=format&fit=crop")
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE WIDGET PREVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp),
                        color = accentColorCompose
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "${widgetTypes[pagerState.currentPage]} (${pagerState.currentPage + 1}/${widgetTypes.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        WidgetPreviewCard(
                            widgetIndex = page,
                            title = songTitle,
                            artist = songArtist,
                            album = songAlbum,
                            artUri = songArtUri,
                            theme = widgetTheme,
                            cornerRadiusFloat = widgetCornerRadius,
                            opacity = widgetOpacity,
                            blur = widgetBlur,
                            accentColor = accentColorCompose,
                            bgCoverArt = bgCoverArt,
                            hideArtThumbnail = hideArtThumbnail,
                            showFavorite = showFavorite,
                            showSecondary = showSecondary,
                            showProgress = showProgress,
                            showSeekButtons = showSeekButtons,
                            showPrevNext = showPrevNext,
                            showShuffleRepeat = showShuffleRepeat,
                            isPlaying = isPlaying
                        )
                    }
                }

                // Dot Indicators
                Row(
                    modifier = Modifier.padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(widgetTypes.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .width(if (isSelected) 14.dp else 5.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) accentColorCompose else Color.White.copy(alpha = 0.35f))
                        )
                    }
                }
            }
        }

        // Bottom 68%: Customization Panel (8.dp Universal Side Margin)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Theme & Accent Color Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "THEME & ACCENT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Theme Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf(
                            "AMOLED" to "AMOLED Black",
                            "GLASS" to "Dark Glass",
                            "FROSTED" to "Frosted",
                            "ACCENT" to "Accent Tint",
                            "GRADIENT" to "Gradient"
                        )
                        themes.forEach { (key, label) ->
                            FilterChip(
                                selected = widgetTheme == key,
                                onClick = { widgetTheme = key; saveAndSync() },
                                label = { Text(label, fontSize = 11.5.sp, fontWeight = if (widgetTheme == key) FontWeight.Bold else FontWeight.Medium) }
                            )
                        }
                    }

                    // Accent Colors Palette
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val colors = listOf(0xFF00E676, 0xFF00E5FF, 0xFFAA00FF, 0xFFFF3D00, 0xFFFFEA00, 0xFFFFFFFF)
                        colors.forEach { colorValue ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorValue.toInt()))
                                    .clickable {
                                        widgetAccentColor = colorValue
                                        saveAndSync()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (widgetAccentColor == colorValue) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 2. Geometry & Blur Sliders Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "GEOMETRY & OPACITY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Corner Radius
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Corner Radius", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("${(widgetCornerRadius * 100).toInt()}%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = widgetCornerRadius,
                            onValueChange = { widgetCornerRadius = it },
                            onValueChangeFinished = { saveAndSync() },
                            valueRange = 0f..1f
                        )
                    }

                    // Background Opacity
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Background Opacity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("${(widgetOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = widgetOpacity,
                            onValueChange = { widgetOpacity = it },
                            onValueChangeFinished = { saveAndSync() },
                            valueRange = 0.2f..1f
                        )
                    }
                }
            }

            // 3. Cover Art & Elements Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DISPLAY ELEMENTS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Album Art as Background", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = bgCoverArt,
                            onCheckedChange = { bgCoverArt = it; saveAndSync() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Interactive Progress Bar", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = showProgress,
                            onCheckedChange = { showProgress = it; saveAndSync() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show Favorite Heart Button", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = showFavorite,
                            onCheckedChange = { showFavorite = it; saveAndSync() }
                        )
                    }
                }
            }

            // Big Pin to Home Screen Button
            Button(
                onClick = {
                    val success = SonicletWidgetManager.requestPinWidget(context, pagerState.currentPage)
                    if (success) {
                        Toast.makeText(context, "Adding widget to home screen...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Long-press Home Screen -> Widgets -> Soniclet", Toast.LENGTH_LONG).show()
                    }
                    saveAndSync()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Selected Widget to Home Screen", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }

            MadeInIndiaFooter(
                topPadding = 10.dp,
                bottomPadding = 8.dp,
                fontSize = 9.5.sp,
                logoHeight = 12.dp,
                logoWidth = 64.dp,
                watermarkAlpha = 0.45f
            )
            Spacer(modifier = Modifier.height(com.kexora.soniclet.LocalPlayerBottomPadding.current + 8.dp))
        }
    }
}

@Composable
private fun WidgetPreviewCard(
    widgetIndex: Int,
    title: String,
    artist: String,
    album: String,
    artUri: Any?,
    theme: String,
    cornerRadiusFloat: Float,
    opacity: Float,
    blur: Float,
    accentColor: Color,
    bgCoverArt: Boolean,
    hideArtThumbnail: Boolean,
    showFavorite: Boolean,
    showSecondary: Boolean,
    showProgress: Boolean,
    showSeekButtons: Boolean,
    showPrevNext: Boolean,
    showShuffleRepeat: Boolean,
    isPlaying: Boolean
) {
    val context = LocalContext.current
    val cornerRadiusDp = (12 + cornerRadiusFloat * 20).dp
    val cornerShape = RoundedCornerShape(cornerRadiusDp)

    val (w, h) = when (widgetIndex) {
        0 -> 90.dp to 90.dp
        1 -> 210.dp to 64.dp
        2 -> 260.dp to 74.dp
        3 -> 135.dp to 135.dp
        4 -> 270.dp to 110.dp
        5 -> 145.dp to 145.dp
        else -> 270.dp to 150.dp
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(w)
                .height(h)
                .clip(cornerShape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), cornerShape)
        ) {
            // Background
            if (bgCoverArt && artUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xCC0C0C10).copy(alpha = (0.55f + (1f - opacity) * 0.35f).coerceIn(0.3f, 0.95f)),
                                    Color(0xEE06060A).copy(alpha = (0.75f + (1f - opacity) * 0.2f).coerceIn(0.5f, 0.98f))
                                )
                            )
                        )
                )
            } else {
                val bgBrush = when (theme) {
                    "GLASS" -> Brush.verticalGradient(listOf(Color(0xCC1A1B24).copy(alpha = opacity), Color(0xCC0E0F14).copy(alpha = opacity)))
                    "FROSTED" -> Brush.verticalGradient(listOf(Color(0xEE222430).copy(alpha = opacity), Color(0xEE12131A).copy(alpha = opacity)))
                    "ACCENT" -> Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.35f * opacity), Color(0xEE0C0D12).copy(alpha = opacity)))
                    "GRADIENT" -> Brush.linearGradient(listOf(Color(0xFF2E1065).copy(alpha = opacity), Color(0xFF0F172A).copy(alpha = opacity)))
                    else -> Brush.verticalGradient(listOf(Color(0xFF050508).copy(alpha = opacity), Color(0xFF000000).copy(alpha = opacity)))
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgBrush)
                )
            }

            // Foreground Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Album Art
                if (!hideArtThumbnail || !bgCoverArt) {
                    Box(
                        modifier = Modifier
                            .size(if (widgetIndex == 0) 36.dp else 46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(artUri).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }

                // Play Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
