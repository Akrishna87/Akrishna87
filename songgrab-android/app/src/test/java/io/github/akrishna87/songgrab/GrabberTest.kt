package io.github.akrishna87.songgrab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GrabberTest {

    @Test
    fun readsTheTidiedTitleAndArtist() {
        val info = Grabber.parseInfo(
            """{"id": "a1", "title": "Some Artist - Some Song", "meta_title": "Some Song", "meta_artist": "Some Artist", "uploader": "Some Artist VEVO", "duration": 215}""",
        )!!
        assertEquals(Grabber.Info("a1", "Some Song", "Some Artist", 215), info)
    }

    @Test
    fun fallsBackToTheUploaderAndReadsNonLatinText() {
        val info = Grabber.parseInfo("""{"id": "a4", "title": "தமிழ்", "uploader": "Channel - Topic", "duration": 5.0}""")!!
        assertEquals("தமிழ்", info.title)
        assertEquals("Channel", info.artist)
        assertEquals(5, info.durationSec)
    }

    @Test
    fun copesWithMissingFields() {
        val info = Grabber.parseInfo("""{"id": "x"}""")!!
        assertEquals("x", info.title)
        assertEquals("", info.artist)
        assertEquals(0, info.durationSec)
        assertNull(Grabber.parseInfo("not json"))
    }

    @Test
    fun readsDownloadProgress() {
        assertEquals(45.3f, Grabber.parsePercent("[download]  45.3% of    3.20MiB at  1.00MiB/s ETA 00:02")!!, 0.01f)
        assertEquals(100f, Grabber.parsePercent("[download] 100% of    3.20MiB in 00:00:03 at 1.00MiB/s")!!, 0.01f)
        assertNull(Grabber.parsePercent("[download] Destination: /x/a1.webm"))
        assertNull(Grabber.parsePercent("[ExtractAudio] Destination: /x/a1.mp3"))
    }

    @Test
    fun asksForTheChosenFormat() {
        val dir = File("/tmp/job")
        val mp3 = Grabber.options(Format.MP3, dir)
        assertEquals("mp3", mp3[mp3.indexOf("--audio-format") + 1])
        assertEquals("/tmp/job/%(id)s.%(ext)s", mp3[mp3.indexOf("-o") + 1])
        assertTrue("--no-playlist" in mp3)
        assertTrue("--embed-thumbnail" in mp3)
        val m4a = Grabber.options(Format.M4A, dir)
        assertEquals("m4a", m4a[m4a.indexOf("--audio-format") + 1])
        assertEquals("bestaudio[ext=m4a]/bestaudio/best", m4a[m4a.indexOf("-f") + 1])
    }

    @Test
    fun asksForAVideoAtTheChosenQuality() {
        val video = Grabber.options(Format.MP4, File("/tmp/job"), Quality.P1080)
        assertEquals("bv*+ba/b", video[video.indexOf("-f") + 1])
        assertEquals("res:1080,vcodec:h264,acodec:m4a", video[video.indexOf("-S") + 1])
        assertEquals("mp4", video[video.indexOf("--merge-output-format") + 1])
        assertFalse("-x" in video) // keeps the picture
        assertEquals("res:480,vcodec:h264,acodec:m4a", Grabber.options(Format.MP4, File("/tmp/job"), Quality.P480).let { it[it.indexOf("-S") + 1] })
    }

    @Test
    fun readsAVideosHeight() {
        assertEquals(720, Grabber.parseInfo("""{"id": "v", "title": "Clip", "height": 720}""")!!.height)
    }

    @Test
    fun explainsCommonFailures() {
        val bot = Exception("WARNING: something\nERROR: [youtube] dQw4w9WgXcQ: Sign in to confirm you’re not a bot. Use --cookies-from-browser")
        assertTrue(Grabber.explain(bot).startsWith("YouTube wants a sign-in"))
        assertEquals("This video is private.", Grabber.explain(Exception("ERROR: [youtube] abc: Private video. Sign in if you've been granted access")))
        assertEquals("This video isn't available: Video unavailable", Grabber.explain(Exception("ERROR: [youtube] dQw4w9WgXcQ: Video unavailable")))
        assertTrue(Grabber.explain(Exception("ERROR: Unable to download webpage: <urlopen error [Errno 7] Unable to resolve host>")).startsWith("Couldn't reach"))
    }

    @Test
    fun onlyUpdatesForProblemsAnUpdateCouldFix() {
        assertTrue(Grabber.mightBeFixedByUpdate(Exception("ERROR: [youtube] abc: Requested format is not available")))
        assertFalse(Grabber.mightBeFixedByUpdate(Exception("ERROR: [youtube] abc: Private video")))
        assertFalse(Grabber.mightBeFixedByUpdate(Exception("Unable to resolve host")))
    }
}
