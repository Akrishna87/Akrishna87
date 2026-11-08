package io.github.akrishna87.saveit

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadsTest {

    @Test
    fun recognisesPostLinks() {
        assertTrue(Threads.isPost("https://www.threads.net/@someone/post/C9AbC_12-xY"))
        assertTrue(Threads.isPost("https://threads.com/@some.one/post/C9AbC12xY?xmt=abc"))
        assertEquals("@some.one" to "C9AbC12xY", Threads.parts("https://www.threads.com/@some.one/post/C9AbC12xY?xmt=abc"))
        assertFalse(Threads.isPost("https://www.threads.net/@someone"))
        assertFalse(Threads.isPost("https://www.instagram.com/reel/C9abc/"))
        // Shared links reach yt-dlp-free handling untouched.
        assertEquals("https://www.threads.net/@someone/post/C9AbC12xY", Links.find("Look https://www.threads.net/@someone/post/C9AbC12xY"))
    }

    @Test
    fun findsTheVideoInThePagesData() {
        val html = """<html><script type="application/json">{"post":{"caption":{"text":"Sunset jam 🌅\nsecond line"},""" +
            """"video_versions":[{"type":101,"url":"https:\/\/scontent.cdninstagram.com\/v\/clip.mp4?a=1&b=2"}]}}</script>""" +
            """<meta property="og:image" content="https://scontent.cdninstagram.com/i/pic.jpg?x=1&amp;y=2"></html>"""
        val post = Threads.parse(html)!!
        assertEquals("https://scontent.cdninstagram.com/v/clip.mp4?a=1&b=2", post.videoUrl)
        assertEquals("Sunset jam 🌅\nsecond line", post.caption)
        assertEquals("https://scontent.cdninstagram.com/i/pic.jpg?x=1&y=2", post.image)
        assertEquals("Sunset jam 🌅", Threads.title(post.caption, "@someone"))
    }

    @Test
    fun fallsBackToMetaTagsAndVideoTags() {
        val meta = """<meta property="og:video" content="https://video.example/v.mp4?s=1&amp;t=2"><meta property="og:description" content="A caption">"""
        assertEquals("https://video.example/v.mp4?s=1&t=2", Threads.parse(meta)!!.videoUrl)
        assertEquals("A caption", Threads.parse(meta)!!.caption)
        val tag = """<video playsinline src="https://video.example/w.mp4"></video>"""
        assertEquals("https://video.example/w.mp4", Threads.parse(tag)!!.videoUrl)
    }

    @Test
    fun saysWhenThereIsNoVideo() {
        assertNull(Threads.parse("""<html><meta property="og:image" content="https://x/y.jpg">Log in to see more</html>"""))
    }

    @Test
    fun namesUntitledVideosAfterTheUser() {
        assertEquals("Threads video by @someone", Threads.title(null, "@someone"))
        assertEquals(80, Threads.title("x".repeat(200), "@someone").length)
    }

    @Test
    fun writesAnInfoFileForYtDlp() {
        val info = JSONObject(
            Threads.infoJson(Threads.Post("https://v.example/a.mp4", "Hello", "https://v.example/a.jpg"), "@me", "ABC", "https://www.threads.com/@me/post/ABC"),
        )
        assertEquals("ABC", info.getString("id"))
        assertEquals("Hello", info.getString("title"))
        assertEquals("@me", info.getString("uploader"))
        assertEquals("https://v.example/a.mp4", info.getString("url"))
        assertEquals("https://v.example/a.jpg", info.getJSONArray("thumbnails").getJSONObject(0).getString("url"))
    }
}
