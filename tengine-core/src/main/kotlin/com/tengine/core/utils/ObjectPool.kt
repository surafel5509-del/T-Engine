package com.tengine.core.utils

/**
 * Zero-GC object pool for high-frequency gameplay entities (projectiles, particles, hit VFX, contacts).
 */
interface Poolable {
    fun onReset()
}

class ObjectPool<T>(
    private val maxCapacity: Int = 512,
    private val onResetItem: ((T) -> Unit)? = null,
    private val factory: () -> T
) {
    private val freeItems = ArrayDeque<T>(maxCapacity.coerceAtMost(256))

    var activeCount: Int = 0
        private set

    val pooledCount: Int
        get() = freeItems.size

    fun prewarm(count: Int) {
        val target = count.coerceAtMost(maxCapacity)
        while (freeItems.size < target) {
            freeItems.addLast(factory())
        }
    }

    fun obtain(): T {
        activeCount++
        return if (freeItems.isNotEmpty()) {
            freeItems.removeLast()
        } else {
            factory()
        }
    }

    fun release(item: T) {
        if (activeCount > 0) activeCount--
        if (item is Poolable) {
            item.onReset()
        }
        onResetItem?.invoke(item)
        if (freeItems.size < maxCapacity) {
            freeItems.addLast(item)
        }
    }

    fun clear() {
        freeItems.clear()
        activeCount = 0
    }
}
