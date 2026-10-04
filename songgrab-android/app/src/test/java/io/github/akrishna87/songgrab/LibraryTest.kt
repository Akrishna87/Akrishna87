package io.github.akrishna87.songgrab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryTest {
    @Test
    fun savesAndReadsSongs() {
        val songs = listOf(
            Song("a1", "Some Song", "Some Artist", "content://media/external/audio/media/12", 215, "MP3", "https://www.youtube.com/watch?v=a1", 1_700_000_000_000, "/data/art/a1.jpg"),
            Song("a2", "தமிழ் பாடல்", "", "file:///sdcard/Music/SongGrab/x.m4a", 0, "M4A", "", 1_700_000_001_000, null),
            Song("v1", "Clip", "Band", "content://media/external/video/media/3", 245, "MP4", "https://youtu.be/v1", 1_700_000_002_000, null, height = 720),
        )
        val read = Library.fromJson(Library.toJson(songs))
        assertEquals(songs, read)
        assertEquals(listOf(false, false, true), read.map { it.isVideo })
    }

    @Test
    fun readsSongsSavedBeforeVideosExisted() {
        val old = Library.fromJson("""[{"id":"a","title":"t","artist":"","uri":"content://x","duration":1,"format":"MP3","source":"","savedAt":1,"art":null}]""")
        assertEquals(0, old.single().height)
        assertFalse(old.single().isVideo)
    }

    @Test
    fun skipsBrokenEntries() {
        assertEquals(1, Library.fromJson("""[{"title":"no uri"}, {"uri":"content://x","title":"ok"}, 5]""").size)
    }

    @Test
    fun makesSafeFileNames() {
        assertEquals("AC DC - Back In Black", Saver.fileName("AC/DC - Back In Black"))
        assertEquals("What", Saver.fileName("What?"))
        assertEquals("Song", Saver.fileName("..."))
        assertEquals(120, Saver.fileName("x".repeat(300)).length)
    }
}
