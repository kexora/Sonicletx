package com.kexora.soniclet.permissions

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kexora.soniclet.R
import com.kexora.soniclet.player.AppTheme
import com.kexora.soniclet.ui.components.common.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Context.findActivity(): android.app.Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is android.app.Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

fun isAudioPermissionGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

fun getAudioPermissionName(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

private data class AppFeature(
    val icon: ImageVector,
    val tag: String,
    val title: String,
    val desc: String,
    val tint: Color
)

@Composable
fun PermissionHandler(
    forceShowTour: Boolean = false,
    onTourDismissed: () -> Unit = {},
    currentTheme: AppTheme = AppTheme.SYSTEM,
    accentColor: Long = 0xFF2979FF,
    onSelectTheme: (AppTheme) -> Unit = {},
    onSelectAccentColor: (Long) -> Unit = {},
    onPermissionGranted: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isStorageGranted by remember { mutableStateOf(isAudioPermissionGranted(context)) }
    var isNotificationGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var isBluetoothGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasFinishedOnboardingStep by rememberSaveable {
        mutableStateOf(isAudioPermissionGranted(context))
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }

    val isFullStorageReady = isStorageGranted

    fun completeOnboardingAndEnter() {
        hasFinishedOnboardingStep = true
        onPermissionGranted()
        onTourDismissed()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val audioNow = isAudioPermissionGranted(context)
                isStorageGranted = audioNow
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    isNotificationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    isBluetoothGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                }
                if (!hasFinishedOnboardingStep && audioNow) {
                    completeOnboardingAndEnter()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionRequestedOnce = true
        val audioNow = permissions[getAudioPermissionName()] ?: isStorageGranted
        isStorageGranted = audioNow
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isNotificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: isNotificationGranted
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isBluetoothGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: isBluetoothGranted
        }
        if (audioNow) {
            completeOnboardingAndEnter()
        }
    }

    fun requestAllPermissions() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val perms = mutableListOf(getAudioPermissionName())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        multiplePermissionsLauncher.launch(perms.toTypedArray())
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    var hasCompletedShowcaseAnimation by rememberSaveable { mutableStateOf(false) }

    // When navigating to page 1 or beyond, mark animation as permanently completed
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage > 0) {
            hasCompletedShowcaseAnimation = true
        }
    }

    if (isStorageGranted && hasFinishedOnboardingStep && !forceShowTour) {
        content()
    } else {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // HorizontalPager enables smooth sliding/swiping between pages
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> {
                            SinglePageFeatureShowcase(
                                accentColor = accentColor,
                                onSelectAccentColor = onSelectAccentColor,
                                isAlreadyCompleted = hasCompletedShowcaseAnimation,
                                onAnimationCompleted = {
                                    hasCompletedShowcaseAnimation = true
                                },
                                onSkip = {
                                    hasCompletedShowcaseAnimation = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isFullStorageReady) {
                                        completeOnboardingAndEnter()
                                    } else {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(1)
                                        }
                                    }
                                },
                                canCloseTour = isFullStorageReady && forceShowTour
                            )
                        }
                        1 -> {
                            PermissionsSetupPage(
                                isStorageGranted = isStorageGranted,
                                isNotificationGranted = isNotificationGranted,
                                isBluetoothGranted = isBluetoothGranted,
                                permissionRequestedOnce = permissionRequestedOnce,
                                accentColor = accentColor,
                                onSelectAccentColor = onSelectAccentColor,
                                onSkip = {
                                    hasCompletedShowcaseAnimation = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isStorageGranted) {
                                        completeOnboardingAndEnter()
                                    } else {
                                        requestAllPermissions()
                                    }
                                },
                                canCloseTour = isStorageGranted,
                                onRequestPermissions = { requestAllPermissions() }
                            )
                        }
                    }
                }

                // Unified Bottom Bar: Page Indicator Dots on LEFT, Pill Action Button on RIGHT
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 2-Dot Page Indicator (Left Side)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(2) { index ->
                                val isSelected = pagerState.currentPage == index
                                val dotWidth by animateDpAsState(
                                    targetValue = if (isSelected) 24.dp else 8.dp,
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                    label = "indicator_dot_width_$index"
                                )
                                val dotColor by animateColorAsState(
                                    targetValue = if (isSelected) Color(accentColor) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    animationSpec = tween(300),
                                    label = "indicator_dot_color_$index"
                                )

                                Box(
                                    modifier = Modifier
                                        .height(8.dp)
                                        .width(dotWidth)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .clickable {
                                            if (index > 0) {
                                                hasCompletedShowcaseAnimation = true
                                            }
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                )
                            }
                        }

                        // Pill Action Button (Right Side)
                        if (pagerState.currentPage == 0) {
                            Button(
                                onClick = {
                                    hasCompletedShowcaseAnimation = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.height(46.dp),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(accentColor),
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Explore Features",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (isFullStorageReady) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        completeOnboardingAndEnter()
                                    } else {
                                        requestAllPermissions()
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("onboarding_primary_action_btn"),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFullStorageReady) Color(0xFF00E676) else Color(accentColor),
                                    contentColor = if (isFullStorageReady) Color(0xFF003816) else Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (isFullStorageReady) "Let's Play" else "Grant Access",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (isFullStorageReady) Icons.Rounded.PlayArrow else Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1. Single Page Cute Feature Showcase:
 * - Adorable Kawaii Soniclet Vinyl Mascot with rotating vinyl, cute headset & micro-equalizer waves.
 * - Interactive "Pick Your Vibe ✨" color palette with real-time accent color switching.
 * - 4 Super cute feature cards with pastel glass tints and friendly descriptions.
 * - Warm, delightful typography and playful layout.
 */
@Composable
private fun SinglePageFeatureShowcase(
    accentColor: Long,
    onSelectAccentColor: (Long) -> Unit,
    onSkip: () -> Unit,
    canCloseTour: Boolean,
    isAlreadyCompleted: Boolean = false,
    onAnimationCompleted: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val primaryColor = Color(accentColor)

    LaunchedEffect(Unit) {
        onAnimationCompleted()
    }

    val vibes = remember {
        listOf(
            0xFFFF5376 to "Rose",
            0xFF9E7BFF to "Lilac",
            0xFF2979FF to "Sonic",
            0xFF00E676 to "Mint",
            0xFFFF9100 to "Peach",
            0xFFFFD600 to "Honey"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar: High Contrast Brand + Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // High contrast adaptive badge for logo visibility in both Dark and Light modes
                SonicletLogoBadge(
                    badgeSize = 36.dp,
                    logoSize = 22.dp,
                    showBorder = true,
                    borderColor = primaryColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "SONICLET",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Offline Music Haven",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = primaryColor
                    )
                }
            }

            TextButton(
                onClick = onSkip,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (canCloseTour) "Close" else "Skip",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cute Kawaii Mascot
            CuteSonicletMascot(
                accentColor = primaryColor,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )

            // Warm Friendly Headline (Zero Emojis, Pure SVG/Clean Typography)
            Text(
                text = "Hey, Music Lover",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Pure sound bliss. 100% offline, zero ads, zero accounts.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Minimalist "Pick Your Music Vibe" (seamless, clean, non-boxed)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pick Your Music Vibe",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    vibes.forEach { (colorLong, name) ->
                        val isSelected = accentColor == colorLong
                        val bubbleColor = Color(colorLong)
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.22f else 1.0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "vibe_scale_$colorLong"
                        )

                        Box(
                            modifier = Modifier
                                .scale(scale)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(bubbleColor)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectAccentColor(colorLong)
                                }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = name,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Clean Minimal Feature List (Pure minimal rows with category pill badges)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MinimalFeatureRow(
                    icon = Icons.Rounded.GraphicEq,
                    category = "ENGINE",
                    title = "Hi-Res Audio Engine",
                    desc = "32-bit floating-point audio engine supporting FLAC, WAV, ALAC, AAC & MP3 with zero distortion.",
                    tint = primaryColor
                )
                MinimalFeatureRow(
                    icon = Icons.Rounded.Equalizer,
                    category = "AUDIO FX",
                    title = "10-Band Equalizer & 3D Audio",
                    desc = "Sub-bass booster, virtualizer, 8D spatial sound, reverb chamber & audiophile presets.",
                    tint = Color(0xFF7C4DFF)
                )
                MinimalFeatureRow(
                    icon = Icons.Rounded.Mic,
                    category = "LYRICS",
                    title = "Live Synchronized Lyrics",
                    desc = "Time-synced karaoke lyrics with smooth auto-scroll and manual timing offset adjustment.",
                    tint = Color(0xFFFF4081)
                )
                MinimalFeatureRow(
                    icon = Icons.Rounded.Vibration,
                    category = "GESTURES",
                    title = "Motion & Shake Gesture Controls",
                    desc = "Shake device to skip track, flip face-down to pause, and edge swipe gesture controls.",
                    tint = Color(0xFF00E676)
                )
                MinimalFeatureRow(
                    icon = Icons.Rounded.DirectionsCar,
                    category = "SAFETY",
                    title = "Drive Mode Experience",
                    desc = "Oversized touch targets, high-contrast distraction-free playback & status voice alerts.",
                    tint = Color(0xFFFF9100)
                )
                MinimalFeatureRow(
                    icon = Icons.Rounded.Edit,
                    category = "TOOLS",
                    title = "On-Device Tag & Artwork Editor",
                    desc = "Edit ID3 metadata, track numbers, artist info, and embed custom album artwork directly.",
                    tint = Color(0xFF00B0FF)
                )
                MinimalFeatureRow(
                    icon = Icons.Rounded.AutoAwesome,
                    category = "PLAYLISTS",
                    title = "Smart Playlists & Mood Categories",
                    desc = "Auto-categorization into Energy, Chill, Workout, Focus & Forgotten Favorites.",
                    tint = Color(0xFFE91E63)
                )
                MinimalFeatureRow(
                    icon = Icons.Rounded.Movie,
                    category = "UTILITY",
                    title = "Video to Audio M4A Converter",
                    desc = "Fast on-device MP4/MKV video audio extractor directly into high-quality M4A files.",
                    tint = Color(0xFFFFC107)
                )
                MinimalFeatureRow(
                    icon = Icons.Rounded.Timer,
                    category = "SLEEP",
                    title = "Smart Sleep Timer",
                    desc = "Gradual volume fade-out with 'Finish Current Song Before Sleep' safety options.",
                    tint = Color(0xFF00BCD4)
                )
                MinimalFeatureRow(
                    icon = Icons.Rounded.Shield,
                    category = "PRIVACY",
                    title = "100% Private & Ad-Free Offline",
                    desc = "Zero internet telemetry, no accounts required, no trackers, and zero ads.",
                    tint = Color(0xFF3F51B5)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MinimalFeatureRow(
    icon: ImageVector,
    category: String,
    title: String,
    desc: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        letterSpacing = (-0.1).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = tint.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = category.uppercase(),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = tint
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 15.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 2. Permissions Setup Page:
 * - Adorable Soniclet Mascot Hero with rotating vinyl, cute headphones & music notes.
 * - Interactive "Pick Your Music Vibe ✨" color palette switcher.
 * - Sleek, glass-tinted permission cards with category/status pill badges and haptic feedback.
 * - Privacy guarantee capsule and helpful app settings guidance.
 */
@Composable
private fun PermissionsSetupPage(
    isStorageGranted: Boolean,
    isNotificationGranted: Boolean,
    isBluetoothGranted: Boolean,
    permissionRequestedOnce: Boolean,
    accentColor: Long = 0xFF2979FF,
    onSelectAccentColor: (Long) -> Unit = {},
    onSkip: () -> Unit = {},
    canCloseTour: Boolean = false,
    onRequestPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val primaryColor = Color(accentColor)
    val isFullStorageReady = isStorageGranted

    val vibes = remember {
        listOf(
            0xFFFF5376 to "Rose",
            0xFF9E7BFF to "Lilac",
            0xFF2979FF to "Sonic",
            0xFF00E676 to "Mint",
            0xFFFF9100 to "Peach",
            0xFFFFD600 to "Honey"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Brand Header (Matches Page 1 Layout Exactly)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SonicletLogoBadge(
                    badgeSize = 36.dp,
                    logoSize = 22.dp,
                    showBorder = true,
                    borderColor = primaryColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "SONICLET",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Step 2 of 2: Permissions",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = primaryColor
                    )
                }
            }

            TextButton(
                onClick = onSkip,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("onboarding_skip_continue_btn")
            ) {
                Text(
                    text = when {
                        isFullStorageReady -> "Close"
                        canCloseTour -> "Continue"
                        else -> "Skip"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        // Main Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Adorable Mascot Hero (Matching Page 1 Quality)
            CutePermissionHero(
                accentColor = primaryColor,
                isGranted = isFullStorageReady,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )

            Text(
                text = if (isFullStorageReady) "You're All Set!" else "Let's Get Tuned In",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (isFullStorageReady) {
                    "Awesome! MediaStore audio & video access is ready — play and manage your music seamlessly."
                } else {
                    "Soniclet works 100% offline. Grant MediaStore audio permissions so we can load your tracks, playlists, and cover art."
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Minimalist "Pick Your Music Vibe" Selector (Shared across Page 1 and Page 2)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pick Your Music Vibe",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    vibes.forEach { (colorLong, name) ->
                        val isSelected = accentColor == colorLong
                        val bubbleColor = Color(colorLong)
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.22f else 1.0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "perm_vibe_scale_$colorLong"
                        )

                        Box(
                            modifier = Modifier
                                .scale(scale)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(bubbleColor)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectAccentColor(colorLong)
                                }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = name,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Permission Row Cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Audio Storage (Required)
                PermissionRowItem(
                    icon = Icons.Rounded.MusicNote,
                    title = "Music & Audio Library",
                    desc = "Scans MediaStore to load your tracks, playlists, and album cover art.",
                    statusTag = "Required",
                    isGranted = isStorageGranted,
                    accentColor = primaryColor,
                    modifier = Modifier.testTag("onboarding_audio_library_card"),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!isStorageGranted) onRequestPermissions()
                    }
                )

                // 2. Lockscreen & Notification Controls (Recommended)
                PermissionRowItem(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Lockscreen Player",
                    desc = "Shows playback controls, track title, and album art on lockscreen.",
                    statusTag = "Recommended",
                    isGranted = isNotificationGranted,
                    accentColor = Color(0xFF8B5CF6),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!isNotificationGranted) onRequestPermissions()
                    }
                )

                // 4. Bluetooth Audio Accessories (Optional)
                PermissionRowItem(
                    icon = Icons.Rounded.BluetoothAudio,
                    title = "Bluetooth Harmony",
                    desc = "Auto-pauses smoothly whenever your wireless headphones disconnect.",
                    statusTag = "Optional",
                    isGranted = isBluetoothGranted,
                    accentColor = Color(0xFF06B6D4),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!isBluetoothGranted) onRequestPermissions()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy guarantee capsule
            Surface(
                shape = CircleShape,
                color = primaryColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.18f)),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "100% On-Device • Your Music Stays Private Forever",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!isStorageGranted && permissionRequestedOnce) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Permissions dialog didn't show? You can enable it directly in App Settings.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = primaryColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open App Settings",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = primaryColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Modern, polished permission row card matching Page 1's sleek aesthetic.
 */
@Composable
private fun PermissionRowItem(
    icon: ImageVector,
    title: String,
    desc: String,
    statusTag: String,
    isGranted: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val cardBgColor = if (isGranted) Color(0xFF00C853).copy(alpha = 0.08f) else accentColor.copy(alpha = 0.06f)
    val cardBorderColor = if (isGranted) Color(0xFF00C853).copy(alpha = 0.35f) else accentColor.copy(alpha = 0.20f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) Color(0xFF00C853).copy(alpha = 0.16f)
                        else accentColor.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Rounded.CheckCircle else icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isGranted) Color(0xFF00C853) else accentColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            letterSpacing = (-0.1).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isGranted -> Color(0xFF00C853).copy(alpha = 0.18f)
                            statusTag == "Required" || statusTag == "Full Access" -> accentColor.copy(alpha = 0.18f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (isGranted) "READY ✓" else statusTag.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp,
                                letterSpacing = 0.4.sp
                            ),
                            color = when {
                                isGranted -> Color(0xFF00C853)
                                statusTag == "Required" || statusTag == "Full Access" -> accentColor
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 15.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Pulsing live dot to indicate active streaming item.
 */
@Composable
private fun PulsingLiveDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_dot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

