package com.kexora.soniclet.ui.components.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import com.kexora.soniclet.R
import kotlinx.coroutines.delay

import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.ui.graphics.Brush

@Composable
fun AnimatedWelcomeHero(
    modifier: Modifier = Modifier,
    accentColor: Long? = null
) {
    val primaryColor = if (accentColor != null) Color(accentColor) else MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    
    val infiniteTransition = rememberInfiniteTransition(label = "modern_welcome_anim")

    // 1. Organic, slow breathing scale (relaxed, premium, 2800ms)
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_breathe"
    )

    // 2. Soundwave ripple 1 expanding outward gently
    val ripple1Scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1_scale"
    )
    val ripple1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1_alpha"
    )

    // 3. Soundwave ripple 2 expanding outward (delayed offset)
    val ripple2Scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, delayMillis = 1300, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2_scale"
    )
    val ripple2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, delayMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2_alpha"
    )

    // 4. Subtle ambient glow intensity
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_intensity"
    )

    // 5. Ambient micro soundwave frequency bars (subtle studio aesthetic)
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wave_b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.75f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wave_b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wave_b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(580, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wave_b4"
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(620, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wave_b5"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(160.dp)
            .padding(8.dp)
    ) {
        // Deep ambient soft glow behind the logo
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = glowIntensity),
                            primaryColor.copy(alpha = glowIntensity * 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Ripple Ring 1 (Soft sound wave emission)
        Box(
            modifier = Modifier
                .size(105.dp)
                .scale(ripple1Scale)
                .border(
                    width = 1.5.dp,
                    color = primaryColor.copy(alpha = ripple1Alpha),
                    shape = RoundedCornerShape(32.dp)
                )
        )

        // Ripple Ring 2 (Offset sound wave emission)
        Box(
            modifier = Modifier
                .size(105.dp)
                .scale(ripple2Scale)
                .border(
                    width = 1.5.dp,
                    color = primaryColor.copy(alpha = ripple2Alpha),
                    shape = RoundedCornerShape(32.dp)
                )
        )

        // Main Premium Logo Card (Breathing scale, steady, elegant)
        Box(
            modifier = Modifier
                .size(105.dp)
                .scale(breatheScale)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            containerColor,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.5f),
                            primaryColor.copy(alpha = 0.15f)
                        )
                    ),
                    shape = RoundedCornerShape(30.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
            ) {
                // Soniclet Brand Emblem with high-contrast adaptive badge
                SonicletLogoBadge(
                    badgeSize = 58.dp,
                    logoSize = 36.dp,
                    showBorder = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Miniature synchronized acoustic frequency wave bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(10.dp)
                ) {
                    val bars = listOf(bar1, bar2, bar3, bar4, bar5)
                    bars.forEachIndexed { index, heightFraction ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight(heightFraction.coerceIn(0.25f, 1f))
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (index == 2) primaryColor
                                    else primaryColor.copy(alpha = 0.65f)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingNote(delayMs: Int, offsetX: Float, initialOffsetY: Float) {
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        startAnimation = true
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "note_anim")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = initialOffsetY,
        targetValue = initialOffsetY - 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "note_y"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2500
                0f at 0
                1f at 500
                1f at 1500
                0f at 2500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "note_alpha"
    )

    if (startAnimation) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = alpha),
            modifier = Modifier
                .offset(x = offsetX.dp, y = offsetY.dp)
                .size(32.dp)
        )
    }
}

@Composable
fun AnimatedThemeHero(accentColor: Long) {
    val infiniteTransition = rememberInfiniteTransition(label = "theme_anim")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "theme_scale"
    )
    
    val animColor by animateColorAsState(
        targetValue = Color(accentColor),
        animationSpec = tween(500),
        label = "theme_color"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(animColor.copy(alpha = 0.3f))
        )
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(1.2f - (scale - 0.8f))
                .clip(CircleShape)
                .background(animColor.copy(alpha = 0.6f))
        )
        Icon(
            imageVector = Icons.Rounded.Palette,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
fun AnimatedGestureHero() {
    val infiniteTransition = rememberInfiniteTransition(label = "swipe_anim")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing, delayMillis = 300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swipe_x"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing, delayMillis = 300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swipe_rot"
    )
    
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        // Track
        Box(modifier = Modifier.width(120.dp).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)))
        
        // Hand
        Icon(
            imageVector = Icons.Rounded.Swipe,
            contentDescription = null,
            modifier = Modifier
                .offset(x = offsetX.dp, y = (-20).dp)
                .graphicsLayer { rotationZ = rotation }
                .size(80.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun AnimatedEqualizerHero() {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_anim")
    
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b4"
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b5"
    )
    val bar6 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b6"
    )
    val bar7 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(460, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b7"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        // Glowing circular backdrop
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        )
        // Equalizer Bars Container
        Row(
            modifier = Modifier
                .height(90.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val bars = listOf(bar1, bar2, bar3, bar4, bar5, bar6, bar7)
            bars.forEachIndexed { index, heightFraction ->
                val barColor = if (index % 2 == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .fillMaxHeight(heightFraction.coerceIn(0.15f, 1f))
                        .clip(RoundedCornerShape(5.dp))
                        .background(barColor)
                )
            }
        }
    }
}

@Composable
fun AnimatedDriveModeHero() {
    val infiniteTransition = rememberInfiniteTransition(label = "drive_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drive_pulse"
    )
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "steer_rot"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(150.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))
        )
        // Inner circle
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .rotate(rotationAngle),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun AnimatedPermissionHero() {
    val infiniteTransition = rememberInfiniteTransition(label = "security_anim")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "security_scale"
    )
    
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        // Outer rings
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale * 1.05f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
        )
        
        Icon(
            imageVector = Icons.Rounded.Security,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Adorable Kawaii Soniclet Vinyl Mascot wearing cute headphones,
 * surrounded by floating musical particles and miniature equalizer bars.
 */
@Composable
fun CuteSonicletMascot(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cute_mascot_anim")

    // Slow rotating vinyl disc
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_spin"
    )

    // Gentle cute bouncing scale (breathing joy)
    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascot_bounce"
    )

    // Subtle head bob angle
    val headBob by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "head_bob"
    )

    // Floating particles offset
    val particleFloat by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "particle_float"
    )

    // Micro equalizer frequency bars
    val eq1 by infiniteTransition.animateFloat(0.3f, 0.9f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq1")
    val eq2 by infiniteTransition.animateFloat(0.8f, 0.35f, infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq2")
    val eq3 by infiniteTransition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq3")
    val eq4 by infiniteTransition.animateFloat(0.9f, 0.4f, infiniteRepeatable(tween(460, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq4")
    val eq5 by infiniteTransition.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(530, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq5")

    Box(
        modifier = modifier.size(165.dp),
        contentAlignment = Alignment.Center
    ) {
        // Deep ambient soft glow
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.35f),
                            accentColor.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Floating Cute Sparkles & Notes
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFFFD54F),
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.TopStart)
                .offset(x = 12.dp, y = (10 + particleFloat).dp)
        )

        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp, y = (8 - particleFloat).dp)
        )

        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color(0xFFFF5252),
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-6).dp, y = (-20 + particleFloat).dp)
        )

        // Mascot Main Body (Vinyl + Headset)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(bounceScale)
                .graphicsLayer { rotationZ = headBob }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(114.dp)
            ) {
                // Vinyl outer record body
                Box(
                    modifier = Modifier
                        .size(98.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF2B283A),
                                    Color(0xFF14121E),
                                    Color(0xFF0C0A14)
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = accentColor.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.08f),
                            radius = size.width * 0.42f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.06f),
                            radius = size.width * 0.33f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = size.width * 0.24f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                // Center Label with high contrast music logo (clean, static & ultra visible in both Dark and Light modes)
                SonicletLogoBadge(
                    badgeSize = 46.dp,
                    logoSize = 28.dp,
                    showBorder = true,
                    borderColor = accentColor
                )

                // Kawaii Headset Arch (Headband across top)
                Canvas(
                    modifier = Modifier.size(114.dp)
                ) {
                    drawArc(
                        color = accentColor,
                        startAngle = 195f,
                        sweepAngle = 150f,
                        useCenter = false,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Left Ear Cushion
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(width = 13.dp, height = 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor)
                        .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                )

                // Right Ear Cushion
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(width = 13.dp, height = 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor)
                        .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Micro pulsating equalizer rhythm bars
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(11.dp)
            ) {
                val bars = listOf(eq1, eq2, eq3, eq4, eq5)
                bars.forEachIndexed { index, fraction ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight(fraction.coerceIn(0.2f, 1f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index == 2) accentColor
                                else accentColor.copy(alpha = 0.65f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Adorable Permission Hero with glowing pastel aura and unlock celebration.
 */
@Composable
fun CutePermissionHero(
    accentColor: Color,
    isGranted: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cute_perm_anim")

    // Slow rotating vinyl disc
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "perm_vinyl_spin"
    )

    // Breathing bounce scale
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "perm_breathe"
    )

    // Floating particles offset
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "perm_float"
    )

    // Micro equalizer frequency bars
    val eq1 by infiniteTransition.animateFloat(0.3f, 0.9f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "perm_eq1")
    val eq2 by infiniteTransition.animateFloat(0.8f, 0.35f, infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "perm_eq2")
    val eq3 by infiniteTransition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "perm_eq3")

    val activeColor = if (isGranted) Color(0xFF00E676) else accentColor

    Box(
        modifier = modifier.size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        // Deep ambient soft radial glow
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeColor.copy(alpha = 0.38f),
                            activeColor.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Floating Cute Sparkles & Music Note
        Icon(
            imageVector = if (isGranted) Icons.Rounded.Star else Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = if (isGranted) Color(0xFFFFD54F) else activeColor,
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.TopStart)
                .offset(x = 10.dp, y = (10 + floatOffset).dp)
        )

        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = activeColor,
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = (8 - floatOffset).dp)
        )

        // Center Cute Mascot Disk
        Box(
            modifier = Modifier
                .size(105.dp)
                .scale(breatheScale),
            contentAlignment = Alignment.Center
        ) {
            // Outer Headphone Arch
            Canvas(modifier = Modifier.size(102.dp)) {
                drawArc(
                    color = activeColor,
                    startAngle = 190f,
                    sweepAngle = 160f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Rotating Vinyl Disc
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF14141A))
                    .rotate(rotation)
                    .border(1.5.dp, activeColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Vinyl Groove Lines
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                )
                
                // Disc Center Label
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(activeColor.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.FolderShared,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }
            }

            // Left Headphone Cup
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-2).dp)
                    .size(16.dp, 26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(activeColor)
            )

            // Right Headphone Cup
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 2.dp)
                    .size(16.dp, 26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(activeColor)
            )
        }

        // Mini Equalizer Waves on Left & Right
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val bars = listOf(eq1, eq2, eq3, eq2, eq1)
            bars.forEach { value ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height((16 * value).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(activeColor.copy(alpha = 0.85f))
                )
            }
        }
    }
}

