package com.kexora.soniclet.ui.components.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kexora.soniclet.R

/**
 * SonicletLogoBadge:
 * A high-contrast, perfectly visible emblem for both Dark Mode and Light Mode.
 *
 * Provides a dedicated adaptive background badge and crisp contrasting tint
 * so the Soniclet logo never washes out or gets lost against light, dark, or tinted surfaces.
 */
@Composable
fun SonicletLogoBadge(
    modifier: Modifier = Modifier,
    badgeSize: Dp = 38.dp,
    logoSize: Dp = 22.dp,
    badgeColor: Color? = null,
    borderColor: Color? = null,
    tintColor: Color? = null,
    showBorder: Boolean = true
) {
    val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background == Color.Black || MaterialTheme.colorScheme.surface.red < 0.25f

    // In Dark Mode: Sleek deep slate/black disc with primary glow border & crisp white logo
    // In Light Mode: Clean crisp white disc with subtle elevation border & deep vibrant primary logo
    val resolvedBadgeColor = badgeColor ?: if (isDark) {
        Color(0xFF0F172A)
    } else {
        Color.White
    }

    val resolvedBorderColor = borderColor ?: if (isDark) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    }

    val resolvedTintColor = tintColor ?: if (isDark) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(resolvedBadgeColor)
            .then(
                if (showBorder) {
                    Modifier.border(1.2.dp, resolvedBorderColor, CircleShape)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_soniclet_app_logo),
            contentDescription = "Soniclet Logo",
            modifier = Modifier.size(logoSize),
            colorFilter = ColorFilter.tint(resolvedTintColor)
        )
    }
}
