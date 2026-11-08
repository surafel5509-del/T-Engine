package com.tengine.core.utils

/**
 * Deterministic game-loop scheduler supporting delayed actions, repeating timers,
 * and conditional waits synchronized with scaled game deltaTime (pauses automatically when Time.timeScale == 0).
 */
class CoroutineScheduler {

    class ScheduledTask internal constructor(
        val id: Long,
        internal var delayRemaining: Float,
        internal val interval: Float,
        internal var repeatCount: Int,
        internal val condition: (() -> Boolean)?,
        internal val ownerTag: Any?,
        internal val callback: () -> Unit
    ) {
        var isCancelled: Boolean = false
            private set

        fun cancel() {
            isCancelled = true
        }
    }

    private var nextId: Long = 1L
    private val tasks = mutableListOf<ScheduledTask>()
    private val pendingAdditions = mutableListOf<ScheduledTask>()

    val activeTaskCount: Int
        get() = tasks.size + pendingAdditions.size

    fun scheduleOnce(delaySeconds: Float, ownerTag: Any? = null, action: () -> Unit): ScheduledTask {
        val task = ScheduledTask(
            id = nextId++,
            delayRemaining = delaySeconds.coerceAtLeast(0f),
            interval = 0f,
            repeatCount = 1,
            condition = null,
            ownerTag = ownerTag,
            callback = action
        )
        pendingAdditions.add(task)
        return task
    }

    fun scheduleRepeating(
        initialDelaySeconds: Float,
        intervalSeconds: Float,
        repeatCount: Int = -1,
        ownerTag: Any? = null,
        action: () -> Unit
    ): ScheduledTask {
        val task = ScheduledTask(
            id = nextId++,
            delayRemaining = initialDelaySeconds.coerceAtLeast(0f),
            interval = intervalSeconds.coerceAtLeast(0.001f),
            repeatCount = repeatCount,
            condition = null,
            ownerTag = ownerTag,
            callback = action
        )
        pendingAdditions.add(task)
        return task
    }

    fun waitUntil(predicate: () -> Boolean, ownerTag: Any? = null, action: () -> Unit): ScheduledTask {
        val task = ScheduledTask(
            id = nextId++,
            delayRemaining = 0f,
            interval = 0f,
            repeatCount = 1,
            condition = predicate,
            ownerTag = ownerTag,
            callback = action
        )
        pendingAdditions.add(task)
        return task
    }

    fun cancelByOwner(ownerTag: Any) {
        for (task in tasks) {
            if (task.ownerTag === ownerTag) task.cancel()
        }
        for (task in pendingAdditions) {
            if (task.ownerTag === ownerTag) task.cancel()
        }
    }

    fun update(dt: Float) {
        if (pendingAdditions.isNotEmpty()) {
            tasks.addAll(pendingAdditions)
            pendingAdditions.clear()
        }
        val iterator = tasks.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            if (task.isCancelled) {
                iterator.remove()
                continue
            }
            val predicate = task.condition
            if (predicate != null) {
                if (predicate()) {
                    task.callback()
                    iterator.remove()
                }
                continue
            }
            task.delayRemaining -= dt
            if (task.delayRemaining <= 0f) {
                task.callback()
                if (task.isCancelled) {
                    iterator.remove()
                } else if (task.repeatCount == 1) {
                    iterator.remove()
                } else {
                    if (task.repeatCount > 1) {
                        task.repeatCount--
                    }
                    task.delayRemaining += task.interval
                    if (task.delayRemaining < 0f) task.delayRemaining = task.interval
                }
            }
        }
    }

    fun clear() {
        tasks.clear()
        pendingAdditions.clear()
    }
}
