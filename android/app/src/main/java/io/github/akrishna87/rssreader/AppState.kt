package io.github.akrishna87.rssreader

/** Whether the app's screen is showing; the background worker stays out of the way while it is. */
object AppState {
    @Volatile var foreground = false
}
