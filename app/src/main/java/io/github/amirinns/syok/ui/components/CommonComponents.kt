package io.github.amirinns.syok.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amirinns.syok.local.ArtworkLoader
import io.github.amirinns.syok.ui.theme.SyokAmberAccent
import io.github.amirinns.syok.ui.theme.SyokAmberContrast
import io.github.amirinns.syok.ui.theme.SyokBorder
import io.github.amirinns.syok.ui.theme.SyokBorderSubtle
import io.github.amirinns.syok.ui.theme.SyokNightBase
import io.github.amirinns.syok.ui.theme.SyokNightCard
import io.github.amirinns.syok.ui.theme.SyokNightSurface
import io.github.amirinns.syok.ui.theme.SyokTextHigh
import io.github.amirinns.syok.ui.theme.SyokTextLow
import io.github.amirinns.syok.ui.theme.SyokTextMed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Renders embedded album artwork off the main thread with caching,
 * falling back to a geometric automotive placeholder on missing/corrupt art.
 */
@Composable
fun ArtworkImage(
    contentUri: String,
    artworkLoader: ArtworkLoader,
    modifier: Modifier = Modifier,
    placeholderTitle: String = "Syok"
) {
    var bitmap by remember(contentUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(contentUri) {
        if (contentUri.isNotBlank()) {
            bitmap = artworkLoader.loadArtwork(contentUri)
        } else {
            bitmap = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SyokNightCard)
            .border(1.dp, SyokBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "Album Artwork",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Automotive geometric dark placeholder
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1E283A), SyokNightBase),
                        center = center,
                        radius = size.minDimension * 0.8f
                    )
                )
                drawCircle(
                    color = SyokAmberAccent.copy(alpha = 0.2f),
                    radius = size.minDimension * 0.25f,
                    center = center
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "♪",
                    fontSize = 32.sp,
                    color = SyokAmberAccent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = placeholderTitle.take(16),
                    fontSize = 12.sp,
                    color = SyokTextMed,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Top system bar with labeled system time, source indicator, and action buttons.
 * Complies with DECISIONS.md S3 (no weather, system time labeled).
 */
@Composable
fun SystemHeaderBar(
    leftContent: @Composable () -> Unit = {},
    rightContent: @Composable () -> Unit = {}
) {
    var systemTime by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10000L)
            systemTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(SyokNightSurface)
            .border(1.dp, SyokBorderSubtle)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            leftContent()
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Labeled system time (automotive safety S3)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(SyokNightBase)
                    .border(1.dp, SyokBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "SYS TIME: $systemTime",
                    color = SyokAmberAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            rightContent()
        }
    }
}

/**
 * Primary Play/Pause driving button (92dp touch target, high contrast amber glow).
 */
@Composable
fun CarPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(92.dp)
            .shadow(12.dp, CircleShape, spotColor = SyokAmberAccent)
            .clip(CircleShape)
            .background(SyokAmberAccent)
            .border(3.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isPlaying) "❚❚" else "▶",
            fontSize = 36.sp,
            color = SyokAmberContrast,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Skip Prev/Next button (78dp touch target for safe driving operation).
 */
@Composable
fun CarSkipButton(
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(SyokNightSurface)
            .border(2.dp, SyokBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 28.sp,
            color = SyokTextHigh,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Secondary car control button (56dp target, e.g. Shuffle, Repeat).
 */
@Composable
fun CarSecondaryButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) SyokAmberAccent.copy(alpha = 0.2f) else SyokNightSurface)
            .border(
                1.dp,
                if (isActive) SyokAmberAccent else SyokBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
            tint = if (isActive) SyokAmberAccent else SyokTextMed
        )
    }
}

/**
 * Coarse seek scrubber with monospace timestamps (Automotive safety S2 & S6).
 */
@Composable
fun CarScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val durationSafe = if (durationMs > 0L) durationMs else 1L
    val fraction = (positionMs.toFloat() / durationSafe.toFloat()).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(durationSafe) {
                    detectTapGestures { offset ->
                        val targetFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek((targetFraction * durationSafe).toLong())
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                // Rail background
                drawRoundRect(
                    color = SyokNightCard,
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    size = size
                )
                // Border
                drawRoundRect(
                    color = SyokBorderSubtle,
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    size = size,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
                // Active fill bar
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(SyokAmberAccent, SyokAmberAccent.copy(alpha = 0.85f))
                    ),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    size = Size(width = size.width * fraction, height = size.height)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(positionMs),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SyokTextMed
            )
            val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
            Text(
                text = "-${formatDuration(remainingMs)}",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SyokTextMed
            )
        }
    }
}

/**
 * Format milliseconds into MM:SS or HH:MM:SS.
 */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
