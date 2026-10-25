package io.github.akrishna87.rssreader

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import io.github.akrishna87.rssreader.data.Article
import io.github.akrishna87.rssreader.data.resolveUrl
import kotlinx.coroutines.withTimeoutOrNull

/** Downloads article images ahead of time so they show up offline (Coil keeps them in its disk cache). */
object Prefetch {
    private val IMG = Regex("<img\\b[^>]*?\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    suspend fun images(context: Context, articles: List<Article>, limit: Int = 60) {
        val urls = LinkedHashSet<String>()
        for (a in articles.take(30)) {
            a.thumbnail?.let(urls::add)
            IMG.findAll(a.fullHtml.ifEmpty { a.contentHtml }).take(3).mapNotNull { resolveUrl(a.link, it.groupValues[1]) }.forEach(urls::add)
            if (urls.size >= limit) break
        }
        val loader = context.imageLoader
        for (url in urls.take(limit)) {
            withTimeoutOrNull(8_000) { loader.execute(ImageRequest.Builder(context).data(url).build()) }
        }
    }
}
