package io.github.akrishna87.songgrab

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class Stage(val active: Boolean) {
    Waiting(true), Starting(true), Downloading(true), Converting(true), Saving(true),
    Done(false), Failed(false), Cancelled(false),
}

/** One link being saved. [progress] is 0–100, or negative when unknown; [part] counts the streams a video comes in. */
data class Job(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val format: Format,
    val quality: Quality = Quality.P720,
    val stage: Stage = Stage.Waiting,
    val title: String? = null,
    val artist: String? = null,
    val progress: Float = -1f,
    val error: String? = null,
    val retried: Boolean = false,
    val part: Int = 0,
)

/** The download queue, shared by the download service and the screen. */
object Jobs {
    private val _all = MutableStateFlow<List<Job>>(emptyList())
    val all: StateFlow<List<Job>> = _all.asStateFlow()

    fun add(job: Job) = _all.update { it + job }

    fun get(id: String): Job? = _all.value.firstOrNull { it.id == id }

    fun update(id: String, change: (Job) -> Job) = _all.update { list -> list.map { if (it.id == id) change(it) else it } }

    fun active(): List<Job> = _all.value.filter { it.stage.active }

    fun remove(id: String) = _all.update { list -> list.filterNot { it.id == id } }

    fun clearFinished() = _all.update { list -> list.filter { it.stage.active || it.stage == Stage.Failed } }

    /** Stops a download: a waiting one is skipped, a running one has its yt-dlp process ended. */
    fun cancel(id: String) {
        val job = get(id) ?: return
        if (!job.stage.active) return
        update(id) { it.copy(stage = Stage.Cancelled) }
        runCatching { Grabber.cancel(id) }
    }
}
