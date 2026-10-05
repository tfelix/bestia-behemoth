package net.bestia.zone.ecs.core

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Decides who may touch the [World] right now; it replaces the lock the world used to hold for a whole tick.
 *
 * Once [bindTickThread] ran, the tick thread owns the world and uses it with no lock at all. Any other
 * thread gets it only between two tasks of the tick thread, as a lease: the tick thread lends the world and
 * waits until it comes back, so the borrower's block runs on its own thread (inside its own transaction)
 * and never at the same time as the tick. Before binding - at boot, and in unit tests - callers share a
 * plain monitor instead.
 */
class WorldOwnership {

  @PublishedApi
  @Volatile
  internal var tickThread: Thread? = null

  @PublishedApi
  @Volatile
  internal var lentTo: Thread? = null

  @PublishedApi
  internal val unboundMonitor = Any()

  private val posted = LinkedBlockingQueue<() -> Unit>()
  private val failureLog = RateLimitedLog()

  /** Runs [block] with the world to itself: inline for its owner, under the monitor while unbound, else on a lease. */
  inline fun <T> guarded(crossinline block: () -> T): T {
    val me = Thread.currentThread()
    if (me === tickThread || me === lentTo || me is WaveWorker) {
      return block()
    }

    if (tickThread == null) {
      // Checked again under the monitor, because binding takes it: nobody is still in here once the tick runs free.
      synchronized(unboundMonitor) {
        if (tickThread == null) return block()
      }
    }

    return borrow { block() }
  }

  /** Makes the calling thread the world's owner. Waits for whoever still holds the monitor. */
  fun bindTickThread() {
    synchronized(unboundMonitor) {
      tickThread = Thread.currentThread()
    }
  }

  fun unbindTickThread() {
    synchronized(unboundMonitor) {
      tickThread = null
    }
  }

  /** Queues [task] for the owner, which runs it between ticks or at the start of the next one. */
  fun post(task: () -> Unit) {
    posted.add(task)
  }

  /** Runs what is queued right now; tasks queued meanwhile wait for the next call. Owner only. */
  fun runQueued() {
    repeat(posted.size) {
      val task = posted.poll() ?: return
      runSafely(task)
    }
  }

  /** Waits until [deadlineNanos] for a task, then runs it and whatever else is queued. Owner only. */
  fun runQueuedUntil(deadlineNanos: Long) {
    val wait = deadlineNanos - java.lang.System.nanoTime()
    if (wait <= 0) return

    val first = posted.poll(wait, TimeUnit.NANOSECONDS) ?: return
    runSafely(first)
    runQueued()
  }

  private fun runSafely(task: () -> Unit) {
    try {
      task()
    } catch (e: Throwable) {
      if (e.isFatal()) throw e
      failureLog.emit { held -> LOG.error(e) { "A task posted to the world failed (+$held more)" } }
    }
  }

  @PublishedApi
  internal fun <T> borrow(block: () -> T): T {
    val lease = Lease(Thread.currentThread())
    posted.add { lend(lease) }

    val started = java.lang.System.nanoTime()
    while (!lease.granted.await(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
      // The tick stopped while we waited: nobody will grant, so fall back to the monitor like before binding.
      if (tickThread == null && lease.cancel()) {
        return synchronized(unboundMonitor) { block() }
      }

      if (java.lang.System.nanoTime() - started > LEASE_TIMEOUT_NANOS && lease.cancel()) {
        throw IllegalStateException("The tick thread did not lend the world within 5 s")
      }
    }

    insideLease.set(true)
    try {
      return block()
    } finally {
      insideLease.set(false)
      lease.returned.countDown()
    }
  }

  private fun lend(lease: Lease) {
    if (!lease.grant()) return

    lentTo = lease.borrower
    try {
      lease.granted.countDown()
      lease.returned.await()
    } finally {
      lentTo = null
    }
  }

  private class Lease(val borrower: Thread) {
    private val state = AtomicInteger(PENDING)
    val granted = CountDownLatch(1)
    val returned = CountDownLatch(1)

    fun grant(): Boolean {
      return state.compareAndSet(PENDING, GRANTED)
    }

    fun cancel(): Boolean {
      return state.compareAndSet(PENDING, CANCELLED)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    private const val PENDING = 0
    private const val GRANTED = 1
    private const val CANCELLED = 2

    private const val POLL_MILLIS = 50L
    private val LEASE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5)

    private val insideLease = ThreadLocal.withInitial { false }

    /** True while the calling thread holds a lease; the SQL guard treats a statement there like one on the tick. */
    fun isInsideLease(): Boolean {
      return insideLease.get()
    }
  }
}
