package io.github.akrishna87.rssreader.data

/** Curated feeds for Discover. If one stops working, its website address usually still does. */
object Directory {
    data class Entry(val name: String, val url: String, val description: String)
    data class Topic(val name: String, val feeds: List<Entry>)

    val topics = listOf(
        Topic("Tech", listOf(
            Entry("Hacker News", "https://hnrss.org/frontpage", "Top stories from the tech community"),
            Entry("The Verge", "https://www.theverge.com/rss/index.xml", "Technology, science, culture"),
            Entry("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "In-depth tech news and analysis"),
            Entry("TechCrunch", "https://techcrunch.com/feed/", "Startups and technology news"),
            Entry("Wired", "https://www.wired.com/feed/rss", "Tech, science and ideas"),
            Entry("GitHub Blog", "https://github.blog/feed/", "Updates from GitHub"),
            Entry("Smashing Magazine", "https://www.smashingmagazine.com/feed/", "Web design and development"),
        )),
        Topic("Cloud & AI", listOf(
            Entry("Azure Blog", "https://azure.microsoft.com/en-us/blog/feed/", "Microsoft Azure announcements"),
            Entry("AWS News Blog", "https://aws.amazon.com/blogs/aws/feed/", "New AWS services and features"),
            Entry("Google Cloud Blog", "https://cloudblog.withgoogle.com/rss/", "Google Cloud news and guides"),
            Entry("Microsoft Research", "https://www.microsoft.com/en-us/research/feed/", "Research from Microsoft"),
            Entry("OpenAI News", "https://openai.com/news/rss.xml", "Announcements from OpenAI"),
            Entry("Google AI", "https://blog.google/technology/ai/rss/", "AI news from Google"),
            Entry("Hugging Face Blog", "https://huggingface.co/blog/feed.xml", "Open-source machine learning"),
            Entry("MIT Technology Review: AI", "https://www.technologyreview.com/topic/artificial-intelligence/feed", "AI coverage and analysis"),
        )),
        Topic("News", listOf(
            Entry("BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "Top stories"),
            Entry("BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", "World news"),
            Entry("NPR News", "https://feeds.npr.org/1001/rss.xml", "US and world news"),
            Entry("The Guardian: World", "https://www.theguardian.com/world/rss", "International news"),
            Entry("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "Global news"),
        )),
        Topic("India", listOf(
            Entry("The Hindu", "https://www.thehindu.com/news/national/feeder/default.rss", "National news"),
            Entry("Times of India", "https://timesofindia.indiatimes.com/rssfeedstopstories.cms", "Top stories"),
            Entry("Indian Express", "https://indianexpress.com/feed/", "Latest news"),
            Entry("NDTV", "https://feeds.feedburner.com/ndtvnews-top-stories", "Top stories"),
            Entry("Mint", "https://www.livemint.com/rss/news", "Business and economy news"),
        )),
        Topic("Business", listOf(
            Entry("The Economic Times", "https://economictimes.indiatimes.com/rssfeedsdefault.cms", "Business news"),
            Entry("Moneycontrol", "https://www.moneycontrol.com/rss/latestnews.xml", "Markets and finance"),
            Entry("Harvard Business Review", "https://feeds.hbr.org/harvardbusiness", "Management and leadership"),
        )),
        Topic("Science", listOf(
            Entry("NASA", "https://www.nasa.gov/news-release/feed/", "News releases"),
            Entry("Quanta Magazine", "https://www.quantamagazine.org/feed/", "Math, physics, biology, CS"),
            Entry("ScienceDaily", "https://www.sciencedaily.com/rss/all.xml", "Latest research news"),
            Entry("New Scientist", "https://www.newscientist.com/feed/home/", "Science and technology news"),
            Entry("Nature", "https://www.nature.com/nature.rss", "Research and news"),
        )),
        Topic("Learning & productivity", listOf(
            Entry("freeCodeCamp", "https://www.freecodecamp.org/news/rss/", "Programming tutorials"),
            Entry("DEV Community", "https://dev.to/feed", "Articles by developers"),
            Entry("Farnam Street", "https://fs.blog/feed/", "Thinking and decision-making"),
            Entry("Cal Newport", "https://calnewport.com/feed/", "Deep work and focus"),
            Entry("Zen Habits", "https://zenhabits.net/feed/", "Simplicity and habits"),
        )),
    )

    /** Sources for "Add from a site": label, example placeholder, hint. */
    enum class Source(val label: String, val placeholder: String, val hint: String) {
        YOUTUBE("YouTube channel", "@veritasium, or a channel link", "A channel's @handle or link. Playlists work too."),
        REDDIT("Subreddit", "r/programming", "A subreddit (r/name) or a user (u/name)."),
        GITHUB("GitHub releases", "microsoft/vscode", "Get new releases of a GitHub project."),
        MASTODON("Mastodon account", "@user@mastodon.social", "A Mastodon (or other fediverse) account."),
        NEWS("News search", "electric cars, or site:bbc.com", "Follow any topic, company or phrase across the news. site:example.com limits it to one site."),
        MEDIUM("Medium / Substack", "@username or name.substack.com", "A Medium writer, or any Substack newsletter."),
    }

    /** Turn what someone typed for [source] into a feed (or page) URL, or null if it doesn't fit. */
    fun feedUrlFor(source: Source, raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        return when (source) {
            Source.YOUTUBE -> {
                Regex("(?:channel/|^)(UC[\\w-]{22})\\b").find(v)?.let { return "https://www.youtube.com/feeds/videos.xml?channel_id=${it.groupValues[1]}" }
                Regex("[?&]list=([\\w-]+)").find(v)?.let { return "https://www.youtube.com/feeds/videos.xml?playlist_id=${it.groupValues[1]}" }
                // A channel page: its feed is found automatically from the page.
                Regex("(?:youtube\\.com/)?@([\\w.-]+)").find(v)?.let { return "https://www.youtube.com/@${it.groupValues[1]}" }
                if ("youtube.com/" in v) normalizeUrl(v) else "https://www.youtube.com/@" + v.removePrefix("@")
            }
            Source.REDDIT -> {
                Regex("(?:reddit\\.com/)?/?(r|u|user)/([\\w-]+)", RegexOption.IGNORE_CASE).find(v)?.let {
                    val kind = if (it.groupValues[1].equals("r", true)) "r" else "user"
                    return "https://www.reddit.com/$kind/${it.groupValues[2]}/.rss"
                }
                val name = v.replace(Regex("^/?r/", RegexOption.IGNORE_CASE), "").trimEnd('/')
                if (Regex("^[\\w-]+$").matches(name)) "https://www.reddit.com/r/$name/.rss" else null
            }
            Source.GITHUB -> Regex("(?:github\\.com/)?([\\w.-]+)/([\\w.-]+?)(?:\\.git)?(?:/|$)").find(v)?.let {
                "https://github.com/${it.groupValues[1]}/${it.groupValues[2]}/releases.atom"
            }
            Source.MASTODON -> {
                Regex("^@?([\\w.]+)@([\\w.-]+\\.[a-z]{2,})$", RegexOption.IGNORE_CASE).find(v)?.let {
                    return "https://${it.groupValues[2]}/@${it.groupValues[1]}.rss"
                }
                Regex("^(?:https?://)?([\\w.-]+)/@([\\w.]+)", RegexOption.IGNORE_CASE).find(v)?.let {
                    "https://${it.groupValues[1]}/@${it.groupValues[2]}.rss"
                }
            }
            Source.NEWS ->
                "https://news.google.com/rss/search?q=" + java.net.URLEncoder.encode(v, "UTF-8") + "&hl=en-US&gl=US&ceid=US:en"
            Source.MEDIUM -> {
                Regex("^@([\\w.-]+)$").find(v)?.let { return "https://medium.com/feed/@${it.groupValues[1]}" }
                Regex("medium\\.com/@([\\w.-]+)").find(v)?.let { return "https://medium.com/feed/@${it.groupValues[1]}" }
                Regex("^(?:https?://)?([\\w-]+)\\.substack\\.com").find(v)?.let { return "https://${it.groupValues[1]}.substack.com/feed" }
                normalizeUrl(v)
            }
        }
    }
}
