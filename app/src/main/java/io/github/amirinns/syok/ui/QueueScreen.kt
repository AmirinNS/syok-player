package io.github.amirinns.syok.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amirinns.syok.ui.components.SystemHeaderBar
import io.github.amirinns.syok.ui.theme.SyokAmberAccent
import io.github.amirinns.syok.ui.theme.SyokBorder
import io.github.amirinns.syok.ui.theme.SyokBorderSubtle
import io.github.amirinns.syok.ui.theme.SyokNightBase
import io.github.amirinns.syok.ui.theme.SyokNightCard
import io.github.amirinns.syok.ui.theme.SyokNightSurface
import io.github.amirinns.syok.ui.theme.SyokTextHigh
import io.github.amirinns.syok.ui.theme.SyokTextLow
import io.github.amirinns.syok.ui.theme.SyokTextMed

@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.playbackState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SyokNightBase)
    ) {
        // System header
        SystemHeaderBar(
            leftContent = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SyokNightSurface)
                        .border(1.dp, SyokBorder, RoundedCornerShape(8.dp))
                        .clickable { onNavigateBack() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "← Back to Player",
                        color = SyokTextHigh,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Text(
                    text = "Playback Queue (${state.totalTracksInQueue} tracks)",
                    color = SyokTextHigh,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        )

        if (state.queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Queue is empty. Select an album from the Library to start playing.",
                    color = SyokTextMed,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(state.queue) { index, item ->
                    val isCurrent = index == state.currentTrackIndex
                    val metadata = item.mediaMetadata
                    val title = metadata.title?.toString()
                        ?: item.mediaId.substringAfterLast(':')
                    val artist = metadata.artist?.toString() ?: "Unknown Artist"
                    val album = metadata.albumTitle?.toString() ?: ""

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) SyokNightCard else SyokNightSurface)
                            .border(
                                1.dp,
                                if (isCurrent) SyokAmberAccent else SyokBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                viewModel.skipToQueueIndex(index)
                                onNavigateBack()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Track index / playing indicator
                        Text(
                            text = if (isCurrent) "▶" else "${index + 1}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isCurrent) SyokAmberAccent else SyokTextLow,
                            modifier = Modifier.width(32.dp)
                        )

                        // Title & Artist
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title,
                                    color = SyokTextHigh,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "[NOW PLAYING]",
                                        color = SyokAmberAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Text(
                                text = "$artist • $album",
                                color = SyokTextMed,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Source badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SyokNightBase)
                                .border(1.dp, SyokBorderSubtle, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "LOCAL",
                                color = SyokAmberAccent,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
