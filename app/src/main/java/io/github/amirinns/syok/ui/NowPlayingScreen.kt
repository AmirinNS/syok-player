package io.github.amirinns.syok.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amirinns.syok.R
import io.github.amirinns.syok.ui.components.ArtworkImage
import io.github.amirinns.syok.ui.components.CarPlayPauseButton
import io.github.amirinns.syok.ui.components.CarScrubber
import io.github.amirinns.syok.ui.components.CarSecondaryButton
import io.github.amirinns.syok.ui.components.CarSkipButton
import io.github.amirinns.syok.ui.components.SystemHeaderBar
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

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSources: () -> Unit = {}
) {
    val state by viewModel.playbackState.collectAsState()
    val folderError by viewModel.folderPlaybackError.collectAsState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SyokNightBase)
    ) {
        // System header bar
        SystemHeaderBar(
            leftContent = {
                // Back to library button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SyokNightSurface)
                        .border(1.dp, SyokBorder, RoundedCornerShape(8.dp))
                        .clickable { onNavigateToLibrary() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "← Library",
                        color = SyokTextHigh,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Source indicator
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SyokNightCard)
                        .border(1.dp, SyokBorderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (state.isLive) "RADIO SOURCE" else "LOCAL SOURCE",
                        color = SyokAmberAccent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            rightContent = {
                // Queue button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SyokNightSurface)
                        .border(1.dp, SyokBorder, RoundedCornerShape(8.dp))
                        .clickable { onNavigateToQueue() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Queue (${state.totalTracksInQueue}) →",
                        color = SyokAmberAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        )

        folderError?.let { FolderRecoveryBanner(it, onNavigateToSources) }

        // Direction A: Asymmetric Split Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column (~40%): Square Art Card (320px - 380px)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SyokNightCard)
                    .border(1.dp, SyokBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                ArtworkImage(
                    contentUri = state.currentContentUri,
                    artworkLoader = viewModel.artworkLoader,
                    modifier = Modifier.fillMaxSize(),
                    placeholderTitle = state.currentTitle
                )

                // Source badge top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (state.isLive) "RADIO SOURCE" else "LOCAL SOURCE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Format badge bottom-right
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (state.isLive) "LIVE" else "AUDIO",
                        color = SyokAmberAccent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right Column (~60%): Metadata, Scrubber/Live, Transport Deck, Up Next
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Metadata block
                Column {
                    Text(
                        text = state.currentTitle,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SyokTextHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.currentArtist,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SyokTextMed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (state.isLive) {
                            state.currentAlbum
                        } else if (state.totalTracksInQueue > 0) {
                            "${state.currentAlbum} • Track ${state.currentTrackIndex + 1} of ${state.totalTracksInQueue}"
                        } else {
                            state.currentAlbum
                        },
                        fontSize = 14.sp,
                        color = SyokTextLow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Stream Error Banner with Retry
                if (state.stationError != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SyokNightCard)
                            .border(2.dp, SyokAmberAccent, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "STREAM STATUS",
                                color = SyokAmberAccent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.stationError!!,
                                color = SyokTextHigh,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = { viewModel.retryStationPlayback() },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SyokAmberAccent,
                                contentColor = SyokAmberContrast
                            )
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                } else if (state.isLive) {
                    // Live Stream indicator (no scrubber)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SyokNightCard)
                            .border(1.dp, SyokBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isPlaying) SyokAmberAccent else SyokTextLow)
                            )
                            Text(
                                text = if (state.isBuffering) "CONNECTING..." else "LIVE BROADCAST",
                                color = SyokAmberAccent,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "INTERNET RADIO",
                            color = SyokTextMed,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Scrubber for local tracks
                    CarScrubber(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        onSeek = { targetMs -> viewModel.seekTo(targetMs) }
                    )
                }

                // Circular Transport Deck
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle (56dp)
                    CarSecondaryButton(
                        iconRes = R.drawable.ic_shuffle,
                        contentDescription = "Shuffle",
                        isActive = state.shuffleEnabled,
                        onClick = { viewModel.toggleShuffle() }
                    )

                    // Prev (78dp)
                    CarSkipButton(
                        symbol = "⏮",
                        onClick = { viewModel.skipPrevious() }
                    )

                    // Play/Pause (92dp)
                    CarPlayPauseButton(
                        isPlaying = state.isPlaying,
                        onClick = { viewModel.togglePlayPause() }
                    )

                    // Next (78dp)
                    CarSkipButton(
                        symbol = "⏭",
                        onClick = { viewModel.skipNext() }
                    )

                    // Repeat (56dp)
                    CarSecondaryButton(
                        iconRes = if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                            R.drawable.ic_repeat_one
                        } else {
                            R.drawable.ic_repeat
                        },
                        contentDescription = "Repeat",
                        isActive = state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                        onClick = { viewModel.toggleRepeat() }
                    )
                }

                // Up Next Banner
                val nextTrack = if (!state.isLive) state.nextTrackTitle else null
                if (nextTrack != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SyokNightSurface)
                            .border(1.dp, SyokBorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { viewModel.skipNext() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "UP NEXT: $nextTrack",
                            color = SyokTextMed,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "TAP TO SKIP →",
                            color = SyokAmberAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
        }
    }
}
