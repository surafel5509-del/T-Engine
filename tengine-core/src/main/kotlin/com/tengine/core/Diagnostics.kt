package com.tengine.core

/**
 * Engine diagnostic logging (Console) and real-time FrameProfiler.
 */
enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}

data class LogEntry(
    val id: Long,
    val timestampSec: Float,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object EngineLogger {
    private const val MAX_ENTRIES = 300
    private var nextId: Long = 1L
    private val entries = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val listeners = mutableListOf<(LogEntry) -> Unit>()

    @Synchronized
    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            id = nextId++,
            timestampSec = Time.unscaledTime,
            level = level,
            tag = tag,
            message = message
        )
        if (entries.size >= MAX_ENTRIES) {
            entries.removeFirst()
        }
        entries.addLast(entry)
        val snapshot = listeners.toList()
        for (listener in snapshot) {
            listener(entry)
        }
    }

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = log(LogLevel.WARNING, tag, message)
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) "$message (${throwable::class.simpleName}: ${throwable.message})" else message
        log(LogLevel.ERROR, tag, full)
    }

    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }
}

/**
 * Per-frame telemetry captured during simulation and rendering for the Studio Profiler dock.
 */
data class ProfilerSample(
    val frameNumber: Long = 0L,
    val fps: Float = 60f,
    val frameTimeMs: Float = 16.6f,
    val physicsTimeMs: Float = 0f,
    val updateTimeMs: Float = 0f,
    val renderTimeMs: Float = 0f,
    val drawCalls: Int = 0,
    val spriteCount: Int = 0,
    val triangleCount: Int = 0,
    val activeGameObjects: Int = 0,
    val physicsBodies: Int = 0,
    val physicsContacts: Int = 0,
    val activeParticles: Int = 0
)

class FrameProfiler(private val historyCapacity: Int = 120) {
    var latest: ProfilerSample = ProfilerSample()
        private set

    private val history = ArrayDeque<ProfilerSample>(historyCapacity)

    @Synchronized
    fun record(sample: ProfilerSample) {
        latest = sample
        if (history.size >= historyCapacity) {
            history.removeFirst()
        }
        history.addLast(sample)
    }

    @Synchronized
    fun historySnapshot(): List<ProfilerSample> = history.toList()

    @Synchronized
    fun clear() {
        history.clear()
    }
}
