package io.github.akrishna87.rssreader.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

/** Pulls the article body out of a web page, for feeds that only send a headline and a snippet. */
object Readability {
    private const val MIN_TEXT = 250
    private const val MAX_HTML = 200_000

    private val NOISE_TAGS = "script, style, noscript, iframe, form, nav, footer, aside, svg, button, input, select, textarea, dialog"
    private val NOISE_CLASS = Regex(
        "comment|sidebar|share|social|related|newsletter|subscribe|promo|advert|cookie|popup|breadcrumb|footer|banner|sponsor|outbrain|taboola|paywall|signup|toolbar",
        RegexOption.IGNORE_CASE,
    )
    private val ARTICLE_SELECTORS = "article, [itemprop=articleBody], main, .post-content, .entry-content, .article-body, .article-content, .story-body, .post-body, #article-body, #content"

    /** Cleaned HTML of the main content, or null if the page doesn't look like an article. */
    fun extract(html: String, baseUrl: String): String? {
        val doc = Jsoup.parse(html, baseUrl)
        doc.select(NOISE_TAGS).remove()
        doc.select("[class], [id]").filter { e ->
            // Keep the likely article containers even if a class name looks noisy (e.g. "share-article").
            e.tagName() !in setOf("body", "html", "article", "main") && NOISE_CLASS.containsMatchIn(e.className() + " " + e.id()) && e.text().length < 1500
        }.forEach { it.remove() }

        // Lazy-loaded images keep the real address in data-src.
        doc.select("img").forEach { img ->
            val lazy = listOf("data-src", "data-lazy-src", "data-original").map { img.attr(it) }.firstOrNull { it.isNotBlank() }
            if (lazy != null && (img.attr("src").isBlank() || img.attr("src").startsWith("data:"))) img.attr("src", lazy)
            if (img.attr("src").isBlank() || img.attr("src").startsWith("data:")) img.remove()
        }

        val best = pick(doc.select(ARTICLE_SELECTORS).toList()) ?: scoreParagraphs(doc.body()) ?: return null
        val cleaned = Jsoup.clean(
            best.html(), baseUrl,
            Safelist.relaxed().removeTags("table", "tbody", "thead", "tr", "td", "th", "col", "colgroup", "caption", "cite").addTags("figure", "figcaption")
                .addAttributes("img", "src", "alt").addAttributes("a", "href"),
        )
        val text = Jsoup.parse(cleaned).text()
        if (text.length < MIN_TEXT) return null
        return cleaned.take(MAX_HTML)
    }

    private fun pick(candidates: List<Element>): Element? {
        val big = candidates.filter { it.text().length >= MIN_TEXT }
        val top = big.maxByOrNull { it.text().length } ?: return null
        // A page-wide wrapper (#content, main) often contains the real article: prefer the inner one when it holds most of the text.
        return big.filter { it !== top && it.parents().contains(top) && it.text().length * 10 >= top.text().length * 6 }
            .maxByOrNull { it.text().length } ?: top
    }

    private fun scoreParagraphs(body: Element?): Element? {
        if (body == null) return null
        val scores = HashMap<Element, Double>()
        body.select("p").forEach { p ->
            val len = p.text().length
            if (len < 40) return@forEach
            val s = 1.0 + p.text().count { it == ',' } + minOf(len / 100, 3)
            val parent = p.parent() ?: return@forEach
            scores[parent] = (scores[parent] ?: 0.0) + s
            parent.parent()?.let { scores[it] = (scores[it] ?: 0.0) + s / 2 }
        }
        return scores.maxByOrNull { it.value }?.key
    }
}
