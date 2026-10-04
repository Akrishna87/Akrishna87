package io.github.akrishna87.songgrab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    private val watch = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    @Test
    fun tidiesEveryKindOfYouTubeLink() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ&t=42s",
            "https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1234567890&index=3",
            "https://www.youtube.com/live/dQw4w9WgXcQ?feature=share",
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
        ).forEach { assertEquals(it, watch, Links.youtube(it)) }
    }

    @Test
    fun findsTheLinkInSharedText() {
        // The YouTube app shares "Title" plus the link; some apps add more words around it.
        assertEquals(watch, Links.find("Check this out https://youtu.be/dQw4w9WgXcQ?si=xyz"))
        assertEquals(watch, Links.find("Song name\nhttps://youtu.be/dQw4w9WgXcQ."))
        assertEquals(watch, Links.find("(https://www.youtube.com/watch?v=dQw4w9WgXcQ)"))
    }

    @Test
    fun prefersYouTubeButKeepsOtherLinks() {
        assertEquals(watch, Links.find("https://example.com/a https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("https://example.com/song.ogg", Links.find("listen: https://example.com/song.ogg"))
    }

    @Test
    fun ignoresTextWithoutALink() {
        assertNull(Links.find(null))
        assertNull(Links.find("   "))
        assertNull(Links.find("just some words"))
    }

    @Test
    fun rejectsYouTubePagesThatArentOneVideo() {
        assertNull(Links.youtube("https://www.youtube.com/playlist?list=PL1234567890"))
        assertNull(Links.youtube("https://www.youtube.com/@SomeChannel"))
        assertNull(Links.youtube("https://www.youtube.com/watch?v=short"))
        assertNull(Links.youtube("https://notyoutube.com/watch?v=dQw4w9WgXcQ"))
    }
}
