package com.kexora.soniclet.easteregg

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Primary Love Card displayed in the library view when Muh Mode is activated.
 * If today is a special anniversary date, it shows the celebration mode.
 * Otherwise, it shows an interactive daily sweet note card with cycling and browsing!
 */
@Composable
fun MuhLoveCard(
    dateInfo: SpecialDateInfo? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Strictly display ONLY to users who have activated Muh Mode
    if (dateInfo == null || !MuhLoveRegistry.isMuhModeActive(context)) return

    val clipboardManager = LocalClipboardManager.current
    var showDiaryDialog by remember { mutableStateOf(false) }

    // Message Cycling State
    var currentIndex by remember(dateInfo) { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }

    val totalMessages = dateInfo.messages.size
    val currentText = remember(dateInfo, currentIndex) {
        if (dateInfo.messages.isNotEmpty()) {
            dateInfo.messages[currentIndex % dateInfo.messages.size]
        } else ""
    }

    // Auto-advance message every 5.5 seconds with smooth visual progress countdown
    LaunchedEffect(dateInfo, currentIndex, isPaused) {
        if (isPaused || totalMessages <= 1) return@LaunchedEffect
        val cycleTimeMs = 5500L
        val updateIntervalMs = 35L
        val totalSteps = (cycleTimeMs / updateIntervalMs).toInt()
        for (step in 1..totalSteps) {
            kotlinx.coroutines.delay(updateIntervalMs)
            progress = step.toFloat() / totalSteps
        }
        progress = 0f
        currentIndex = (currentIndex + 1) % totalMessages
    }

    // Dynamic festive celebration animations
    val infiniteTransition = rememberInfiniteTransition(label = "celebration_fx")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_shift"
    )
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_pulse"
    )

    if (showDiaryDialog) {
        MuhLoveDiaryDialog(
            dateInfo = dateInfo,
            onDismiss = { showDiaryDialog = false }
        )
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B071A).copy(alpha = 0.94f)
        ),
        border = BorderStroke(
            1.5.dp,
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF4081),
                    Color(0xFFFFD700),
                    Color(0xFF8E24AA),
                    Color(0xFFFF1493)
                ),
                start = Offset(gradientShift * 300f, 0f),
                end = Offset((1f - gradientShift) * 600f, 600f)
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 1. Festive Particle Background Canvas (Floating glowing hearts & sparkles)
            CelebrationParticleCanvas(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
            )

            // 2. Main Card Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pulsing Celebration Badge
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFFFF4081).copy(alpha = 0.45f),
                                            Color(0xFF8E24AA).copy(alpha = 0.15f)
                                        )
                                    )
                                )
                                .border(1.dp, Color(0xFFFF4081).copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dateInfo.celebrationIcon,
                                fontSize = 20.sp,
                                modifier = Modifier.scale(heartScale)
                            )
                        }

                        Surface(
                            color = Color(0xFFFF1493).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, Color(0xFFFF4081).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = dateInfo.badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.5.sp
                                ),
                                color = Color(0xFFFF80AB),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                            )
                        }
                    }

                    // Live auto-cycling indicator chip
                    Surface(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, Color(0xFFFF4081).copy(alpha = 0.3f)),
                        modifier = Modifier.clickable {
                            isPaused = !isPaused
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isPaused) Color(0xFFFFD54F) else Color(0xFF69F0AE))
                            )
                            Text(
                                text = if (isPaused) "Paused" else "${currentIndex + 1}/$totalMessages",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                ),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title & Subtitle Banner
                Text(
                    text = dateInfo.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.5.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = dateInfo.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.75f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Auto-Changing Message Bubble
                Surface(
                    color = Color.Black.copy(alpha = 0.38f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF4081).copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Tap on message advances immediately
                            progress = 0f
                            currentIndex = (currentIndex + 1) % totalMessages
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // Smooth Progress Timer Bar (Visual Story Countdown)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFFFF4081),
                                                Color(0xFFFFD700)
                                            )
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // High-Energy Animated Message Transition
                        AnimatedContent(
                            targetState = currentText,
                            transitionSpec = {
                                (slideInVertically(
                                    animationSpec = spring(
                                        dampingRatio = 0.72f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) { height -> height / 2 } + fadeIn(tween(350)) + scaleIn(initialScale = 0.94f)) togetherWith
                                (slideOutVertically(
                                    animationSpec = tween(280)
                                ) { height -> -height / 2 } + fadeOut(tween(220)) + scaleOut(targetScale = 1.04f))
                            },
                            label = "message_transition"
                        ) { targetText ->
                            Text(
                                text = "“ $targetText ”",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 22.sp,
                                    fontSize = 14.5.sp
                                ),
                                color = Color(0xFFFFF0F5)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "✨ Auto-advances every 5s • Tap note to flip anytime",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Actions Row (All Notes, Copy, Share)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Browse All Diary Button
                    Surface(
                        onClick = { showDiaryDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF1493).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFFFF4081).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MenuBook,
                                contentDescription = "Browse Diary",
                                tint = Color(0xFFFF80AB),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Notes (${totalMessages})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFFFF80AB)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Copy Note
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(currentText))
                                Toast.makeText(context, "Note copied! 💌", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy Note",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Share Note
                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "${dateInfo.celebrationIcon} ${dateInfo.title}\n\n\"$currentText\"\n\n— Dedicated with Love in Soniclet ✨")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Special Note"))
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share Note",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating Celebration Particle Canvas for glowing ambient effects.
 */
@Composable
private fun CelebrationParticleCanvas(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val particleCount = 16
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        for (i in 0 until particleCount) {
            val seed = ((i * 37) % 100) / 100f
            val speed = 0.6f + (i % 5) * 0.15f
            val currentYProgress = (phase * speed + seed) % 1f
            val y = height * (1f - currentYProgress)
            val wobbleX = kotlin.math.sin((currentYProgress * 2 * Math.PI + i).toDouble()).toFloat() * 20.dp.toPx()
            val baseX = (width / (particleCount + 1)) * (i + 1)
            val x = (baseX + wobbleX).coerceIn(10f, width - 10f)

            val alpha = (kotlin.math.sin(currentYProgress * Math.PI).toFloat() * 0.45f).coerceIn(0f, 0.45f)
            val radius = (2.5.dp + ((i % 3) * 1.5f).dp).toPx()

            val color = when (i % 4) {
                0 -> Color(0xFFFF4081)
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFBA68C8)
                else -> Color(0xFFFF80AB)
            }

            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Backwards compatible function for directly celebrating a special date.
 */
@Composable
fun SpecialDateCelebrationCard(
    dateInfo: SpecialDateInfo,
    modifier: Modifier = Modifier
) {
    MuhLoveCard(dateInfo = dateInfo, modifier = modifier)
}

/**
 * Fullscreen Interactive Love Notes Diary & Memory Vault.
 * Allows reading, searching, filtering, and copying all 200+ messages right in the app!
 */
@Composable
fun MuhLoveDiaryDialog(
    dateInfo: SpecialDateInfo? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    if (!MuhLoveRegistry.isMuhModeActive(context)) {
        onDismiss()
        return
    }

    val clipboardManager = LocalClipboardManager.current
    val allMessages = remember(dateInfo) {
        if (dateInfo != null) {
            val dateLabel = when (dateInfo.month) {
                java.util.Calendar.MARCH -> "21 March"
                java.util.Calendar.JULY -> "1 July"
                java.util.Calendar.NOVEMBER -> "23 November"
                else -> ""
            }
            dateInfo.messages.map { msg ->
                MuhLoveMessage(
                    category = dateInfo.title,
                    dateLabel = dateLabel,
                    icon = dateInfo.celebrationIcon,
                    text = msg
                )
            }
        } else {
            MuhLoveRegistry.getAllLoveMessages()
        }
    }

    var selectedFilterIndex by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var surpriseNote by remember { mutableStateOf<MuhLoveMessage?>(null) }

    val filterCategories = remember(dateInfo, allMessages.size) {
        if (dateInfo != null) {
            listOf("Special Notes (${allMessages.size})" to "")
        } else {
            listOf(
                "All (${allMessages.size})" to "",
                "🎂 Birthday" to "Birthday",
                "💬 First Chat" to "Chat",
                "💍 Proposal" to "Proposal"
            )
        }
    }

    val filteredList = remember(selectedFilterIndex, searchQuery, allMessages) {
        val catKeyword = filterCategories[selectedFilterIndex].second
        allMessages.filter { note ->
            val matchesCategory = if (catKeyword.isEmpty()) true else note.category.contains(catKeyword, ignoreCase = true)
            val matchesSearch = if (searchQuery.isBlank()) true else note.text.contains(searchQuery.trim(), ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE91E63).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFE91E63),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Karishma's Love Diary 💌",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Special Notes, Riddles & Memories (${filteredList.size} notes)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search messages, paheli, memories...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE91E63),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filterCategories.indices.toList()) { index ->
                        val isSelected = selectedFilterIndex == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilterIndex = index },
                            label = { Text(filterCategories[index].first) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE91E63),
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Surprise Note Button Banner
                Surface(
                    color = Color(0xFFE91E63).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE91E63).copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            surpriseNote = if (filteredList.isNotEmpty()) filteredList.random() else allMessages.random()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFE91E63),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (surpriseNote == null) "Tap here for a Random Surprise Love Note! 🎲✨" else "Tap for Another Surprise Note! 🎲",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFE91E63)
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // If Surprise Note is selected, show it on top!
                if (surpriseNote != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.5.dp, Color(0xFFE91E63)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(surpriseNote!!.icon, fontSize = 18.sp)
                                    Text(
                                        text = "🎲 Surprise: ${surpriseNote!!.category}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFE91E63)
                                    )
                                }
                                IconButton(
                                    onClick = { surpriseNote = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Close Surprise", modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = surpriseNote!!.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Messages List
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💔", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No messages found matching \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredList) { note ->
                            LoveMessageCard(
                                note = note,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(note.text))
                                    Toast.makeText(context, "Copied note! 💌", Toast.LENGTH_SHORT).show()
                                },
                                onShare = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, note.text)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Love Note"))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoveMessageCard(
    note: MuhLoveMessage,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(note.icon, fontSize = 16.sp)
                    Surface(
                        color = Color(0xFFE91E63).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = note.dateLabel.ifEmpty { note.category },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE91E63),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
