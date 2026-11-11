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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amirinns.syok.ui.components.ArtworkImage
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
fun LibraryScreen(
    viewModel: PlayerViewModel,
    hasPermission: Boolean,
    onGrantPermissionRequested: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albums by viewModel.albumsFlow.collectAsState()
    val trackCount by viewModel.trackCountFlow.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SyokNightBase)
    ) {
        // System header bar
        SystemHeaderBar(
            leftContent = {
                // Source tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SyokAmberAccent)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Local Files ($trackCount)",
                        color = SyokAmberContrast,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Rescan button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SyokNightCard)
                        .border(1.dp, SyokBorder, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isScanning) {
                            viewModel.scanMedia()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isScanning) "Scanning..." else "Rescan",
                        color = SyokTextHigh,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            rightContent = {
                // Now Playing button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SyokNightSurface)
                        .border(1.dp, SyokBorder, RoundedCornerShape(8.dp))
                        .clickable { onNavigateToNowPlaying() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Now Playing →",
                        color = SyokAmberAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        )

        // Main content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                !hasPermission -> {
                    PermissionEmptyState(onGrantRequested = onGrantPermissionRequested)
                }
                albums.isEmpty() -> {
                    NoMusicEmptyState(
                        isScanning = isScanning,
                        onRescan = { viewModel.scanMedia() }
                    )
                }
                else -> {
                    // 4-column responsive album grid (Direction A)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(albums, key = { it.title }) { album ->
                            AlbumCard(
                                album = album,
                                artworkLoader = viewModel.artworkLoader,
                                onClick = {
                                    viewModel.playAlbum(album)
                                    onNavigateToNowPlaying()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Persistent Mini Player at bottom (80dp height)
        if (playbackState.totalTracksInQueue > 0 || playbackState.currentMediaItem != null) {
            MiniPlayerBar(
                playbackState = playbackState,
                artworkLoader = viewModel.artworkLoader,
                onPlayPause = { viewModel.togglePlayPause() },
                onSkipPrev = { viewModel.skipPrevious() },
                onSkipNext = { viewModel.skipNext() },
                onExpand = onNavigateToNowPlaying
            )
        }
    }
}

@Composable
fun AlbumCard(
    album: AlbumUiModel,
    artworkLoader: io.github.amirinns.syok.local.ArtworkLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SyokNightSurface)
            .border(1.dp, SyokBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        ArtworkImage(
            contentUri = album.firstContentUri,
            artworkLoader = artworkLoader,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            placeholderTitle = album.title
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            color = SyokTextHigh,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = album.artist,
            color = SyokTextMed,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${album.tracksCount} TRACKS",
                color = SyokTextLow,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
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

@Composable
fun MiniPlayerBar(
    playbackState: PlaybackUiState,
    artworkLoader: io.github.amirinns.syok.local.ArtworkLoader,
    onPlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(SyokNightSurface)
            .border(1.dp, SyokBorder)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left info box (click to expand)
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onExpand() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkImage(
                contentUri = playbackState.currentContentUri,
                artworkLoader = artworkLoader,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                placeholderTitle = playbackState.currentTitle
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = playbackState.currentTitle,
                    color = SyokTextHigh,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playbackState.currentArtist} • ${playbackState.currentAlbum}",
                    color = SyokTextMed,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Right controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Prev (52dp)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SyokNightCard)
                    .border(1.dp, SyokBorder, CircleShape)
                    .clickable { onSkipPrev() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⏮", color = SyokTextHigh, fontSize = 20.sp)
            }

            // Play / Pause (60dp, amber)
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(SyokAmberAccent)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (playbackState.isPlaying) "❚❚" else "▶",
                    color = SyokAmberContrast,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Next (52dp)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SyokNightCard)
                    .border(1.dp, SyokBorder, CircleShape)
                    .clickable { onSkipNext() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⏭", color = SyokTextHigh, fontSize = 20.sp)
            }

            // EXPAND button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SyokNightCard)
                    .border(1.dp, SyokBorder, RoundedCornerShape(8.dp))
                    .clickable { onExpand() }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "EXPAND",
                    color = SyokAmberAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PermissionEmptyState(
    onGrantRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(SyokNightSurface)
                .border(2.dp, SyokAmberAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "!", fontSize = 48.sp, color = SyokAmberAccent, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Kebenaran Audio Diperlukan",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SyokTextHigh
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Syok needs permission to scan audio files on this Android head unit and any plugged-in USB/SD drives. Your private music remains local and is never uploaded.",
            fontSize = 15.sp,
            color = SyokTextMed,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.65f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .height(76.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SyokAmberAccent)
                .clickable { onGrantRequested() }
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Grant Storage Access (Scan Audio)",
                color = SyokAmberContrast,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "• Driving Safety: Initial scans should be executed while vehicle is safely parked.",
            fontSize = 12.sp,
            color = SyokTextLow
        )
    }
}

@Composable
fun NoMusicEmptyState(
    isScanning: Boolean,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No Music Found",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SyokTextHigh
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "No audio files detected in storage. Place your MP3 or FLAC files in the device's Music folder and tap Rescan.",
            fontSize = 15.sp,
            color = SyokTextMed,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.6f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .height(60.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SyokAmberAccent)
                .clickable(enabled = !isScanning) { onRescan() }
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isScanning) "Scanning Media..." else "Rescan Audio",
                color = SyokAmberContrast,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
