package net.bestia.zone.ecs

import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Entity lock for thread-safe access
 */
class EntityLock(val entity: Entity) : AutoCloseable {
  private val lock = ReentrantReadWriteLock()
  private var isLocked = false

  fun acquireReadLock() {
    lock.readLock().lock()
    isLocked = true
  }

  fun acquireWriteLock() {
    lock.writeLock().lock()
    isLocked = true
  }

  override fun close() {
    if (isLocked) {
      try {
        lock.writeLock().unlock()
      } catch (e: IllegalMonitorStateException) {
        try {
          lock.readLock().unlock()
        } catch (e: IllegalMonitorStateException) {
          // Already unlocked
        }
      }
      isLocked = false
    }
  }
}