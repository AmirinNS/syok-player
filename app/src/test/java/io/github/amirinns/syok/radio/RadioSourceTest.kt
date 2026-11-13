package io.github.amirinns.syok.radio

import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RadioSourceTest {

    @Test
    fun stationMediaId_formattingAndParsing() {
        val mediaId = RadioSource.stationMediaId(42L)
        assertEquals("station:42", mediaId)

        assertTrue(RadioSource.isStationMediaId("station:42"))
        assertFalse(RadioSource.isStationMediaId("mediastore:external:42"))
        assertFalse(RadioSource.isStationMediaId("syok:local"))
        assertFalse(RadioSource.isStationMediaId(null))

        assertEquals(42L, RadioSource.parseStationId("station:42"))
        assertNull(RadioSource.parseStationId("station:abc"))
        assertNull(RadioSource.parseStationId("mediastore:42"))
        assertNull(RadioSource.parseStationId(null))
    }

    @Test
    fun createStationBrowseItem_hasLiveSemanticsAndPlayableFlag() {
        val station = StationEntity(id = 5L, name = "Radio Paradise", url = "https://stream.radioparadise.com/aac-128")
        val item = RadioSource.createStationBrowseItem(station)

        assertEquals("station:5", item.mediaId)
        assertEquals("Radio Paradise", item.mediaMetadata.title.toString())
        assertTrue(item.mediaMetadata.isPlayable == true)
        assertFalse(item.mediaMetadata.isBrowsable == true)
        assertEquals(MediaMetadata.MEDIA_TYPE_RADIO_STATION, item.mediaMetadata.mediaType)
    }

    @Test
    fun createStationPlayableItem_mp3Stream_configuresLiveAndUrl() {
        val station = StationEntity(id = 10L, name = "Jazz Groove", url = "https://west-mp3-128.jazzgroove.org")
        val item = RadioSource.createStationPlayableItem(station)

        assertEquals("station:10", item.mediaId)
        assertEquals("https://west-mp3-128.jazzgroove.org", item.localConfiguration?.uri.toString())
        assertNotNull(item.liveConfiguration)
        assertTrue(item.mediaMetadata.isPlayable == true)
        assertFalse(item.mediaMetadata.isBrowsable == true)
    }

    @Test
    fun createStationPlayableItem_hlsStream_setsHlsMimeType() {
        val station = StationEntity(id = 20L, name = "Apple Music 1 HLS", url = "https://example.com/live/master.m3u8")
        val item = RadioSource.createStationPlayableItem(station)

        assertEquals("station:20", item.mediaId)
        assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration?.mimeType)
        assertNotNull(item.liveConfiguration)
    }
}
