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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kexora.soniclet.player.AppTheme
import com.kexora.soniclet.ui.components.common.CutePermissionHero
import com.kexora.soniclet.ui.components.common.CuteSonicletMascot
import com.kexora.soniclet.ui.components.common.SonicletLogoBadge
import kotlinx.coroutines.launch

private val OnboardingBackdrop = Color(0xFF060908)
private val ReadyEmerald = Color(0xFF00E676)
private val MutedText = Color(0xFFA0ADA6)
private val HairlineColor = Color(0x14FFFFFF)

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

@Composable
fun PermissionHandler(
    forceShowTour: Boolean = false,
    onTourDismissed: () -> Unit = {},
    currentTheme: AppTheme = AppTheme.SYSTEM,
    accentColor: Long = 0xFF00E676,
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
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var isBluetoothGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
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
                    isNotificationGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    isBluetoothGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
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

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage > 0) {
            hasCompletedShowcaseAnimation = true
        }
    }

    if (isStorageGranted && hasFinishedOnboardingStep && !forceShowTour) {
        content()
    } else {
        val animatedAccent by animateColorAsState(
            targetValue = Color(accentColor),
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            label = "onboarding_accent"
        )
        val accentContentColor = remember(animatedAccent) {
            if (animatedAccent.luminance() > 0.52f) Color(0xFF060908) else Color.White
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OnboardingBackdrop)
                .drawBehind {
                    val glowRadius = size.width * 0.78f
                    val glowCenter = Offset(size.width * 0.5f, size.height * 0.23f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedAccent.copy(alpha = 0.22f),
                                animatedAccent.copy(alpha = 0.07f),
                                Color.Transparent
                            ),
                            center = glowCenter,
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = glowCenter
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .align(Alignment.Center)
            ) {
                // Clean Minimal Top Header
                OnboardingTopHeader(
                    currentStep = pagerState.currentPage + 1,
                    accentColor = animatedAccent,
                    skipLabel = if (isFullStorageReady && forceShowTour) "Close" else "Skip",
                    onSkip = {
                        hasCompletedShowcaseAnimation = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (pagerState.currentPage == 0) {
                            if (isFullStorageReady) {
                                completeOnboardingAndEnter()
                            } else {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }
                        } else {
                            if (isFullStorageReady) {
                                completeOnboardingAndEnter()
                            } else {
                                requestAllPermissions()
                            }
                        }
                    }
                )

                // 2-Step Pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> WelcomeOnboardingStep(
                            accentColorLong = accentColor,
                            animatedAccent = animatedAccent,
                            onSelectAccentColor = onSelectAccentColor
                        )
                        1 -> PermissionsOnboardingStep(
                            isStorageGranted = isStorageGranted,
                            isNotificationGranted = isNotificationGranted,
                            isBluetoothGranted = isBluetoothGranted,
                            permissionRequestedOnce = permissionRequestedOnce,
                            animatedAccent = animatedAccent,
                            onRequestPermissions = { requestAllPermissions() }
                        )
                    }
                }

                // Sleek Fixed Bottom Bar
                OnboardingBottomBar(
                    currentPage = pagerState.currentPage,
                    animatedAccent = animatedAccent,
                    accentContentColor = accentContentColor,
                    isStorageGranted = isFullStorageReady,
                    onSelectPage = { targetPage ->
                        if (targetPage > 0) hasCompletedShowcaseAnimation = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                    },
                    onPrimaryClick = {
                        if (pagerState.currentPage == 0) {
                            hasCompletedShowcaseAnimation = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        } else {
                            if (isFullStorageReady) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                completeOnboardingAndEnter()
                            } else {
                                requestAllPermissions()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OnboardingTopHeader(
    currentStep: Int,
    accentColor: Color,
    skipLabel: String,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SonicletLogoBadge(
                badgeSize = 32.dp,
                logoSize = 19.dp,
                showBorder = true,
                borderColor = accentColor.copy(alpha = 0.72f)
            )
            Text(
                text = "SONICLET",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp
            )
            Spacer(modifier = Modifier.width(2.dp))
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Text(
                    text = "$currentStep / 2",
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }

        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .height(48.dp)
                .testTag("onboarding_skip_continue_btn"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color.White.copy(alpha = 0.68f)
            )
        ) {
            Text(
                text = skipLabel,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    animatedAccent: Color,
    accentContentColor: Color,
    isStorageGranted: Boolean,
    onSelectPage: (Int) -> Unit,
    onPrimaryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated Capsule Pager Dots (Left)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { index ->
                val isSelected = currentPage == index
                val dotWidth by animateDpAsState(
                    targetValue = if (isSelected) 18.dp else 6.dp,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    label = "pager_dot_w_$index"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isSelected) animatedAccent else Color.White.copy(alpha = 0.24f),
                    animationSpec = tween(260),
                    label = "pager_dot_c_$index"
                )
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clickable(
                            onClickLabel = "Step ${index + 1}",
                            onClick = { onSelectPage(index) }
                        )
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }

        // Full-Pill Primary Button (Right)
        val buttonBg = if (currentPage == 1 && isStorageGranted) ReadyEmerald else animatedAccent
        val buttonFg = if (currentPage == 1 && isStorageGranted) Color(0xFF060908) else accentContentColor

        Button(
            onClick = onPrimaryClick,
            shape = CircleShape,
            modifier = Modifier
                .height(48.dp)
                .testTag("onboarding_primary_action_btn"),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonBg,
                contentColor = buttonFg
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            if (currentPage == 0) {
                Text(
                    text = "Continue",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
            } else {
                Text(
                    text = if (isStorageGranted) "Start Listening" else "Allow Access",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isStorageGranted) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeOnboardingStep(
    accentColorLong: Long,
    animatedAccent: Color,
    onSelectAccentColor: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    val accentPalette = remember {
        listOf(
            0xFF00E676L to "Green",
            0xFF2979FFL to "Blue",
            0xFF9E7BFFL to "Purple",
            0xFFFF5376L to "Pink",
            0xFFFF9100L to "Orange",
            0xFFFFD600L to "Yellow"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CuteSonicletMascot(
            accentColor = animatedAccent
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Welcome to Soniclet",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Your offline music player. No ads, no login needed.",
            color = MutedText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Compact Color Picker (Step 1 ONLY)
        Text(
            text = "Pick your favorite color",
            color = MutedText.copy(alpha = 0.86f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            accentPalette.forEach { (colorLong, label) ->
                val isSelected = accentColorLong == colorLong
                val dotColor = Color(colorLong)
                val dotScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "color_dot_scale_$colorLong"
                )
                val checkTint = if (dotColor.luminance() > 0.55f) Color(0xFF060908) else Color.White

                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 48.dp)
                        .testTag("onboarding_color_${label.lowercase()}")
                        .clickable(
                            onClickLabel = "Choose $label accent color",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectAccentColor(colorLong)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .scale(dotScale)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.dp, Color.White, CircleShape)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "$label selected",
                                tint = checkTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4 Simple, Box-Free Highlights
        Column(modifier = Modifier.fillMaxWidth()) {
            OnboardingHighlightRow(
                icon = Icons.Rounded.MusicNote,
                title = "Crystal Clear Sound",
                subtitle = "Plays all your songs smoothly without any ads",
                iconTint = animatedAccent
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = HairlineColor
            )
            OnboardingHighlightRow(
                icon = Icons.Rounded.Equalizer,
                title = "Bass Boost & Equalizer",
                subtitle = "Adjust bass, vocals, and sound just the way you like",
                iconTint = animatedAccent
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = HairlineColor
            )
            OnboardingHighlightRow(
                icon = Icons.Rounded.Lyrics,
                title = "Live Song Lyrics",
                subtitle = "Read and sing along while your music plays",
                iconTint = animatedAccent
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = HairlineColor
            )
            OnboardingHighlightRow(
                icon = Icons.Rounded.ContentCut,
                title = "Edit Songs & Ringtones",
                subtitle = "Change song names, cover photos, and cut ringtones",
                iconTint = animatedAccent
            )
        }
    }
}

@Composable
private fun OnboardingHighlightRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = MutedText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionsOnboardingStep(
    isStorageGranted: Boolean,
    isNotificationGranted: Boolean,
    isBluetoothGranted: Boolean,
    permissionRequestedOnce: Boolean,
    animatedAccent: Color,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CutePermissionHero(
            accentColor = animatedAccent,
            isGranted = isStorageGranted
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isStorageGranted) "You're All Set" else "Find Your Songs",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isStorageGranted) {
                "Your music library is ready to play offline."
            } else {
                "Allow Soniclet to find and play the music saved on your phone."
            },
            color = MutedText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        // 3 Flat, Box-Free Edge-to-Edge Permission Rows
        Column(modifier = Modifier.fillMaxWidth()) {
            FlatPermissionRow(
                icon = Icons.Rounded.LibraryMusic,
                title = "Your Music & Audio",
                subtitle = "Finds all songs, playlists, and cover photos on your phone",
                pillLabel = if (isStorageGranted) "READY ✓" else "REQUIRED",
                isReady = isStorageGranted,
                accentColor = animatedAccent,
                modifier = Modifier.testTag("onboarding_audio_library_card"),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!isStorageGranted) onRequestPermissions()
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = HairlineColor
            )
            FlatPermissionRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "Lockscreen Controls",
                subtitle = "Play, pause, or change songs right from your lock screen",
                pillLabel = "READY ✓",
                isReady = true,
                accentColor = animatedAccent,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!isNotificationGranted) onRequestPermissions()
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = HairlineColor
            )
            FlatPermissionRow(
                icon = Icons.Rounded.Headphones,
                title = "Headphone Auto-Pause",
                subtitle = "Pauses your music automatically when earphones disconnect",
                pillLabel = "READY ✓",
                isReady = true,
                accentColor = animatedAccent,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!isBluetoothGranted) onRequestPermissions()
                }
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Simple Inline Privacy Note (No Box)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MutedText.copy(alpha = 0.78f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "100% offline • Your songs stay private on your phone",
                color = MutedText.copy(alpha = 0.82f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        if (!isStorageGranted && permissionRequestedOnce) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Didn't see the prompt? Turn on Music access in Settings.",
                color = Color(0xFFFF8A80),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
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
                },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("onboarding_open_settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = animatedAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Open Settings",
                    color = animatedAccent,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FlatPermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    pillLabel: String,
    isReady: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val iconColor = if (isReady) ReadyEmerald else accentColor
    val pillBg = if (isReady) ReadyEmerald.copy(alpha = 0.14f) else accentColor.copy(alpha = 0.15f)
    val pillTextColor = if (isReady) ReadyEmerald else accentColor

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = pillBg
                ) {
                    Text(
                        text = pillLabel,
                        color = pillTextColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = MutedText,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
