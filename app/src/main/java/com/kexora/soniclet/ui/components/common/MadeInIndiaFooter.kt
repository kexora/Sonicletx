package com.kexora.soniclet.ui.components.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.R

/**
 * Clean & modern watermark branding footer:
 * "Made in India with ❤️ by [KEXORA SVG Logo]"
 * Styled with soft opacity blend effect.
 */
@Composable
fun MadeInIndiaFooter(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 16.dp,
    topPadding: Dp = 24.dp,
    watermarkAlpha: Float = 0.65f,
    fontSize: TextUnit = 11.5.sp,
    logoHeight: Dp = 18.dp,
    logoWidth: Dp = 96.dp,
    heartSize: Dp = 12.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = bottomPadding, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.alpha(watermarkAlpha),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Made in India with",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Love",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(heartSize)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "by",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(5.dp))
            Image(
                painter = painterResource(id = R.drawable.ic_kexora_logo),
                contentDescription = "Kexora",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(logoHeight)
                    .width(logoWidth)
            )
        }
    }
}
