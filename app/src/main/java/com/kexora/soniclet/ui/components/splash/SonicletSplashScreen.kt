package com.kexora.soniclet.ui.components.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.R
import com.kexora.soniclet.easteregg.MuhLoveRegistry
import com.kexora.soniclet.easteregg.SpecialDateEntry
import com.kexora.soniclet.player.AppTheme
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 3-Tier Splash Screen Mode:
 * - TIER_1_STANDARD: Standard modern splash (1350ms)
 * - TIER_2_SPECIAL_MODE: Special Mode active on regular days (1500ms)
 * - TIER_3_SPECIAL_DATE: Special Mode active AND today matches an enabled SpecialDateEntry (2200ms + tap to enter)
 */
enum class SonicletSplashTier {
    TIER_1_STANDARD,
    TIER_2_SPECIAL_MODE,
    TIER_3_SPECIAL_DATE
}

/**
 * Resolves which splash tier should render on launch or preview.
 */
fun resolveSonicletSplashTier(
    isSpecialModeEnabled: Boolean,
    specialDates: List<SpecialDateEntry>,
    forcedTier: SonicletSplashTier? = null,
    forcedSpecialDate: SpecialDateEntry? = null
): Pair<SonicletSplashTier, SpecialDateEntry?> {
    if (forcedTier != null) {
        return when (forcedTier) {
            SonicletSplashTier.TIER_1_STANDARD -> SonicletSplashTier.TIER_1_STANDARD to null
            SonicletSplashTier.TIER_2_SPECIAL_MODE -> SonicletSplashTier.TIER_2_SPECIAL_MODE to null
            SonicletSplashTier.TIER_3_SPECIAL_DATE -> {
                val matched = forcedSpecialDate
                    ?: MuhLoveRegistry.getTodayMatchedSpecialDateEntry(specialDates)
                    ?: specialDates.firstOrNull { it.isEnabled }
                    ?: specialDates.firstOrNull()
                    ?: MuhLoveRegistry.getDefaultSpecialDateEntries().first()
                SonicletSplashTier.TIER_3_SPECIAL_DATE to matched
            }
        }
    }

    if (!isSpecialModeEnabled) {
        return SonicletSplashTier.TIER_1_STANDARD to null
    }

    val todayMatch = MuhLoveRegistry.getTodayMatchedSpecialDateEntry(
        entries = specialDates,
        calendar = Calendar.getInstance()
    )
    return if (todayMatch != null) {
        SonicletSplashTier.TIER_3_SPECIAL_DATE to todayMatch
    } else {
        SonicletSplashTier.TIER_2_SPECIAL_MODE to null
    }
}

private data class SplashParticle(
    val xFraction: Float,
    val initialYFraction: Float,
    val speed: Float,
    val radiusDp: Float,
    val phaseOffset: Float,
    val isHeart: Boolean,
    val isSparkle: Boolean,
    val colorIndex: Int
)

/**
 * Dedicated 3-Tier Animated Compose Splash Screen for Soniclet.
 * Automatically selects and renders:
 *  - Tier 1: Standard Modern Splash (1350ms)
 *  - Tier 2: Special Mode Active Splash (1500ms)
 *  - Tier 3: Special Date Celebration Splash (2200ms + tap anywhere to enter immediately)
 */
@Composable
fun SonicletSplashScreen(
    appTheme: AppTheme,
    isSpecialModeEnabled: Boolean,
    specialDates: List<SpecialDateEntry>,
    forcedTier: SonicletSplashTier? = null,
    forcedSpecialDate: SpecialDateEntry? = null,
    onSplashFinished: () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = when (appTheme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> systemDark
    }

    val (activeTier, matchedSpecialDate) = remember(
        isSpecialModeEnabled,
        specialDates,
        forcedTier,
        forcedSpecialDate
    ) {
        resolveSonicletSplashTier(
            isSpecialModeEnabled = isSpecialModeEnabled,
            specialDates = specialDates,
            forcedTier = forcedTier,
            forcedSpecialDate = forcedSpecialDate
        )
    }

    var hasDismissed by remember { mutableStateOf(false) }
    val dismissNow = {
        if (!hasDismissed) {
            hasDismissed = true
            onSplashFinished()
        }
    }

    // Entry spring scale (0.80f -> 1.0f) and wordmark slide/fade (10.dp -> 0.dp)
    val emblemScale = remember { Animatable(0.80f) }
    val contentAlpha = remember { Animatable(0f) }
    val wordmarkOffsetY = remember { Animatable(10f) }

    val durationMs = when (activeTier) {
        SonicletSplashTier.TIER_1_STANDARD -> 1350L
        SonicletSplashTier.TIER_2_SPECIAL_MODE -> 1500L
        SonicletSplashTier.TIER_3_SPECIAL_DATE -> 2200L
    }

    LaunchedEffect(activeTier, forcedTier, forcedSpecialDate) {
        launch {
            emblemScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            contentAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }
        launch {
            wordmarkOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing)
            )
        }
        delay(durationMs)
        dismissNow()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "soniclet_splash_infinite")

    // Outer soundwave pulse ring (1.0f -> 1.24f, fading alpha)
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splash_soundwave_pulse"
    )
    val pulseScale = 1.0f + (0.24f * pulseProgress)
    val pulseAlpha = (1.0f - pulseProgress) * 0.48f

    // Secondary delayed pulse ring for Tier 2 & Tier 3
    val secondaryPulseProgress = (pulseProgress + 0.5f) % 1.0f
    val secondaryPulseScale = 1.0f + (0.30f * secondaryPulseProgress)
    val secondaryPulseAlpha = (1.0f - secondaryPulseProgress) * 0.34f

    // Orbiting SweepGradient halo angle for Tier 2 & Tier 3
    val haloRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splash_halo_rotation"
    )

    // 60fps particle drift phase (0f -> 1f)
    val particleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "splash_particle_time"
    )

    // 4-bar equalizer wave animations
    val eqBar1 by infiniteTransition.animateFloat(
        initialValue = 0.32f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 380, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_1"
    )
    val eqBar2 by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 440, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_2"
    )
    val eqBar3 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_3"
    )
    val eqBar4 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 410, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_4"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val roseGoldColor = Color(0xFFFFB6C1)
    val champagneColor = Color(0xFFFFD580)
    val warmPinkColor = Color(0xFFFF7597)

    val backdropColor = when (activeTier) {
        SonicletSplashTier.TIER_1_STANDARD -> {
            if (isDarkTheme) Color(0xFF060807) else Color(0xFFF6F8F7)
        }
        SonicletSplashTier.TIER_2_SPECIAL_MODE -> Color(0xFF08060A)
        SonicletSplashTier.TIER_3_SPECIAL_DATE -> Color(0xFF0A060C)
    }

    val titleTextColor = when (activeTier) {
        SonicletSplashTier.TIER_1_STANDARD -> MaterialTheme.colorScheme.onBackground
        SonicletSplashTier.TIER_2_SPECIAL_MODE,
        SonicletSplashTier.TIER_3_SPECIAL_DATE -> Color.White
    }

    val footerTextColor = when (activeTier) {
        SonicletSplashTier.TIER_1_STANDARD -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        SonicletSplashTier.TIER_2_SPECIAL_MODE,
        SonicletSplashTier.TIER_3_SPECIAL_DATE -> Color.White.copy(alpha = 0.58f)
    }

    // Pre-computed deterministic particles for Tier 2 (bokeh light motes) & Tier 3 (hearts + sparkles)
    val particles = remember(activeTier) {
        val count = when (activeTier) {
            SonicletSplashTier.TIER_1_STANDARD -> 0
            SonicletSplashTier.TIER_2_SPECIAL_MODE -> 20
            SonicletSplashTier.TIER_3_SPECIAL_DATE -> 28
        }
        List(count) { i ->
            val seed = (i * 73 + 19) % 100
            val xFrac = ((i * 37 + 13) % 88 + 6) / 100f
            val yFrac = ((i * 53 + 7) % 90 + 5) / 100f
            val speed = 0.55f + (seed % 45) / 70f
            val radius = 2.8f + (i % 5) * 1.35f
            val isHeart = activeTier == SonicletSplashTier.TIER_3_SPECIAL_DATE && (i % 3 == 0)
            val isSparkle = activeTier == SonicletSplashTier.TIER_3_SPECIAL_DATE && (i % 3 == 1)
            SplashParticle(
                xFraction = xFrac,
                initialYFraction = yFrac,
                speed = speed,
                radiusDp = radius,
                phaseOffset = (i * 0.42f),
                isHeart = isHeart,
                isSparkle = isSparkle,
                colorIndex = i % 3
            )
        }
    }

    val allowTapToEnter = activeTier == SonicletSplashTier.TIER_3_SPECIAL_DATE || forcedTier != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdropColor)
            .then(
                if (allowTapToEnter) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { dismissNow() }
                    )
                } else {
                    Modifier
                }
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("soniclet_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Ambient radial glow & 60fps Canvas particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.44f)
            val glowRadius = size.minDimension * 0.68f

            when (activeTier) {
                SonicletSplashTier.TIER_1_STANDARD -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.22f),
                                primaryColor.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = center
                    )
                }

                SonicletSplashTier.TIER_2_SPECIAL_MODE -> {
                    // Warm dual-tone radial glow blending primary and soft rose-gold/champagne
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.24f),
                                roseGoldColor.copy(alpha = 0.16f),
                                champagneColor.copy(alpha = 0.07f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = glowRadius * 1.08f
                        ),
                        radius = glowRadius * 1.08f,
                        center = center
                    )

                    // Floating bokeh light motes drifting upward
                    val palette = listOf(primaryColor, roseGoldColor, champagneColor)
                    particles.forEach { p ->
                        val progress = (p.initialYFraction - particleTime * p.speed).mod(1f)
                        val sway = sin((particleTime * 2f * PI + p.phaseOffset).toFloat()) * 14.dp.toPx()
                        val px = (size.width * p.xFraction) + sway
                        val py = size.height * progress
                        val edgeFade = when {
                            progress < 0.15f -> progress / 0.15f
                            progress > 0.85f -> (1f - progress) / 0.15f
                            else -> 1f
                        }.coerceIn(0f, 1f)
                        val color = palette[p.colorIndex]
                        val r = p.radiusDp.dp.toPx()
                        drawCircle(
                            color = color.copy(alpha = 0.26f * edgeFade),
                            radius = r * 1.9f,
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.45f * edgeFade),
                            radius = r * 0.65f,
                            center = Offset(px, py)
                        )
                    }
                }

                SonicletSplashTier.TIER_3_SPECIAL_DATE -> {
                    // Celebratory dual-aura radial glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                warmPinkColor.copy(alpha = 0.26f),
                                primaryColor.copy(alpha = 0.18f),
                                champagneColor.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = glowRadius * 1.18f
                        ),
                        radius = glowRadius * 1.18f,
                        center = center
                    )

                    // 60fps rising glowing hearts & sparkles
                    val palette = listOf(warmPinkColor, champagneColor, primaryColor)
                    particles.forEach { p ->
                        val progress = (p.initialYFraction - particleTime * p.speed).mod(1f)
                        val sway = sin((particleTime * 2f * PI + p.phaseOffset).toFloat()) * 18.dp.toPx()
                        val px = (size.width * p.xFraction) + sway
                        val py = size.height * progress
                        val edgeFade = when {
                            progress < 0.14f -> progress / 0.14f
                            progress > 0.86f -> (1f - progress) / 0.14f
                            else -> 1f
                        }.coerceIn(0f, 1f)
                        val color = palette[p.colorIndex]
                        val r = p.radiusDp.dp.toPx()

                        if (p.isHeart) {
                            drawGlowingHeart(
                                center = Offset(px, py),
                                sizePx = r * 3.1f,
                                color = color.copy(alpha = 0.55f * edgeFade)
                            )
                        } else if (p.isSparkle) {
                            drawFourPointSparkle(
                                center = Offset(px, py),
                                radiusPx = r * 2.4f,
                                color = champagneColor.copy(alpha = 0.65f * edgeFade)
                            )
                        } else {
                            drawCircle(
                                color = color.copy(alpha = 0.32f * edgeFade),
                                radius = r * 1.7f,
                                center = Offset(px, py)
                            )
                        }
                    }
                }
            }
        }

        // Main Center Stack
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val emblemSize: Dp = if (activeTier == SonicletSplashTier.TIER_3_SPECIAL_DATE) 96.dp else 92.dp

            // Center Hero Emblem with Spring Scale & Pulsing Soundwave Rings
            Box(
                modifier = Modifier
                    .size(emblemSize + 44.dp)
                    .scale(emblemScale.value),
                contentAlignment = Alignment.Center
            ) {
                // Primary Outer Soundwave Ring (1.0f -> 1.24f, fading alpha)
                Box(
                    modifier = Modifier
                        .size(emblemSize)
                        .scale(pulseScale)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = if (activeTier == SonicletSplashTier.TIER_3_SPECIAL_DATE) {
                                warmPinkColor
                            } else {
                                primaryColor
                            },
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Secondary Warm Aura Ring for Tier 2 & Tier 3
                if (activeTier != SonicletSplashTier.TIER_1_STANDARD) {
                    Box(
                        modifier = Modifier
                            .size(emblemSize)
                            .scale(secondaryPulseScale)
                            .alpha(secondaryPulseAlpha)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = champagneColor,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }

                // Rotating SweepGradient Luminous Halo for Tier 2 & Tier 3
                if (activeTier != SonicletSplashTier.TIER_1_STANDARD) {
                    Canvas(
                        modifier = Modifier
                            .size(emblemSize + 10.dp)
                            .rotate(haloRotation)
                    ) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.9f),
                                    roseGoldColor.copy(alpha = 0.85f),
                                    champagneColor.copy(alpha = 0.9f),
                                    Color.Transparent,
                                    primaryColor.copy(alpha = 0.9f)
                                )
                            ),
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Soniclet Circular Headphones + Vinyl Emblem (92.dp / 96.dp)
                val emblemContainerColor = when (activeTier) {
                    SonicletSplashTier.TIER_1_STANDARD -> {
                        if (isDarkTheme) Color(0xFF0F1512) else Color(0xFFFFFFFF)
                    }
                    SonicletSplashTier.TIER_2_SPECIAL_MODE -> Color(0xFF131018)
                    SonicletSplashTier.TIER_3_SPECIAL_DATE -> Color(0xFF16101C)
                }

                Box(
                    modifier = Modifier
                        .size(emblemSize)
                        .clip(CircleShape)
                        .background(emblemContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    // Subtle inner vinyl grooves + radial sheen
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.24f),
                                    if (activeTier != SonicletSplashTier.TIER_1_STANDARD) {
                                        roseGoldColor.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    },
                                    Color.Transparent
                                )
                            ),
                            radius = r
                        )
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.34f),
                            radius = r - 1.2.dp.toPx(),
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.12f),
                            radius = r * 0.76f,
                            style = Stroke(width = 0.8.dp.toPx())
                        )
                    }

                    Image(
                        painter = painterResource(id = R.drawable.ic_soniclet_app_logo),
                        contentDescription = "Soniclet Emblem",
                        modifier = Modifier.size(emblemSize * 0.58f),
                        colorFilter = ColorFilter.tint(primaryColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Crisp SONICLET Wordmark (24.sp Bold, letterSpacing = 3.5.sp) sliding upward (10.dp -> 0.dp)
            Column(
                modifier = Modifier
                    .offset(y = wordmarkOffsetY.value.dp)
                    .alpha(contentAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SONICLET",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.5.sp
                    ),
                    color = titleTextColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Animated 4-bar Vertical Audio Equalizer Wave (primary color)
                Row(
                    modifier = Modifier.height(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val barHeights = listOf(eqBar1, eqBar2, eqBar3, eqBar4)
                    barHeights.forEachIndexed { idx, fraction ->
                        val barColor = when {
                            activeTier == SonicletSplashTier.TIER_3_SPECIAL_DATE && idx % 2 == 1 -> roseGoldColor
                            else -> primaryColor
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 3.5.dp, height = (18f * fraction.coerceIn(0.25f, 1f)).dp)
                                .clip(CircleShape)
                                .background(barColor)
                        )
                    }
                }

                // Tier 2 Badge: "✨ SPECIAL EDITION"
                if (activeTier == SonicletSplashTier.TIER_2_SPECIAL_MODE) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = CircleShape,
                        color = primaryColor.copy(alpha = 0.16f),
                        tonalElevation = 0.dp
                    ) {
                        Text(
                            text = "✨ SPECIAL EDITION",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.1.sp
                            ),
                            color = primaryColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                // Tier 3 Celebration Content: Borderless Date Pill + Bold Headline + Warm Greeting Subtitle
                if (activeTier == SonicletSplashTier.TIER_3_SPECIAL_DATE && matchedSpecialDate != null) {
                    Spacer(modifier = Modifier.height(18.dp))

                    // Borderless CircleShape Date Pill ("🎉 TODAY IS SPECIAL • 22 Oct")
                    Surface(
                        shape = CircleShape,
                        color = warmPinkColor.copy(alpha = 0.18f),
                        tonalElevation = 0.dp
                    ) {
                        Text(
                            text = "🎉 TODAY IS SPECIAL • ${matchedSpecialDate.formattedShortDate()}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = roseGoldColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Special Date Bold Headline (22.sp Bold)
                    Text(
                        text = matchedSpecialDate.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 28.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 320.dp)
                    )

                    if (matchedSpecialDate.message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        // Warm 1-2 line Greeting Subtitle (14.sp, Color.White.copy(alpha = 0.85f))
                        Text(
                            text = matchedSpecialDate.message,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 20.sp
                            ),
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 310.dp)
                        )
                    }
                }
            }
        }

        // Minimal Footer anchored cleanly at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
                .alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (activeTier == SonicletSplashTier.TIER_3_SPECIAL_DATE) {
                Text(
                    text = "Tap anywhere to continue",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.42f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "by Kexora",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp
                ),
                color = footerTextColor
            )
        }
    }
}

private fun DrawScope.drawGlowingHeart(
    center: Offset,
    sizePx: Float,
    color: Color
) {
    val half = sizePx / 2f
    val path = Path().apply {
        moveTo(center.x, center.y + half * 0.75f)
        cubicTo(
            center.x - half * 1.2f, center.y + half * 0.1f,
            center.x - half * 0.95f, center.y - half * 0.85f,
            center.x, center.y - half * 0.25f
        )
        cubicTo(
            center.x + half * 0.95f, center.y - half * 0.85f,
            center.x + half * 1.2f, center.y + half * 0.1f,
            center.x, center.y + half * 0.75f
        )
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawFourPointSparkle(
    center: Offset,
    radiusPx: Float,
    color: Color
) {
    val inner = radiusPx * 0.26f
    val path = Path().apply {
        moveTo(center.x, center.y - radiusPx)
        quadraticTo(center.x + inner, center.y - inner, center.x + radiusPx, center.y)
        quadraticTo(center.x + inner, center.y + inner, center.x, center.y + radiusPx)
        quadraticTo(center.x - inner, center.y + inner, center.x - radiusPx, center.y)
        quadraticTo(center.x - inner, center.y - inner, center.x, center.y - radiusPx)
        close()
    }
    drawPath(path = path, color = color)
}
