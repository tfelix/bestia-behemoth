package net.bestia.zone.ecs.core

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs database and other blocking work off the tick thread. Network sends never come here: they leave
 * through the tick's outbox, so a slow query cannot delay anybody's packets.
 *
 * ### Ordering
 * A job submitted with a [submit] `key` is guaranteed to never run concurrently with another job
 * sharing that key, and runs strictly in submission order relative to it (one single-threaded
 * worker per key, picked by hash). This matters whenever a job does a read-modify-write against
 * shared state - e.g. two loots racing to persist the same master's DB inventory row would
 * otherwise be able to interleave into a lost update. Jobs with different keys may run fully in
 * parallel. Always key by a stable domain id (e.g. a masterId or accountId), never by a transient
 * ECS entity id.
 *
 * ### Bounded
 * Each worker queues at most [queueCapacity] jobs. A job that does not fit is dropped and counted rather
 * than run on the caller, because the caller is usually the tick, and the tick must never wait on the DB.
 */
@Service
class AsyncJobExecutor(
  workerCount: Int = 4,
  private val queueCapacity: Int = 2048,
) {
  private val workers: List<ThreadPoolExecutor> = List(workerCount) { i ->
    ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(queueCapacity)) { r ->
      Thread({
        ON_WORKER.set(true)
        r.run()
      }, "zone-db-job-$i")
    }
  }

  private val rejected = AtomicLong()
  private val rejectionLog = RateLimitedLog()
  private val backlogLog = RateLimitedLog(intervalMillis = 10_000L)

  /** Jobs waiting on all workers right now. */
  val pendingJobs: Int
    get() {
      return workers.sumOf { it.queue.size }
    }

  /** Jobs dropped since start because their worker's queue was full. */
  val rejectedJobs: Long
    get() {
      return rejected.get()
    }

  /** Runs [job] on a background worker, keeping jobs sharing [key] strictly ordered. */
  fun submit(key: Any, job: () -> Unit) {
    val worker = workerFor(key.hashCode())

    try {
      worker.execute { runSafely(job) }
    } catch (_: RejectedExecutionException) {
      val total = rejected.incrementAndGet()
      rejectionLog.emit { held -> LOG.error { "DB job for $key dropped, its queue is full ($total dropped so far, +$held)" } }
      return
    }

    if (worker.queue.size > queueCapacity / 2) {
      backlogLog.emit { _ -> LOG.warn { "DB jobs are backing up: $pendingJobs waiting" } }
    }
  }

  /**
   * Blocks until every job queued on [key] so far has run, so the caller reads what they wrote. Never waits
   * on a worker thread: two workers waiting on each other would never finish.
   */
  fun awaitPending(key: Any, timeoutSeconds: Long = 10L) {
    if (ON_WORKER.get() == true) return

    try {
      workerFor(key.hashCode()).submit {}.get(timeoutSeconds, TimeUnit.SECONDS)
    } catch (_: TimeoutException) {
      LOG.warn { "Jobs for $key did not finish within $timeoutSeconds s; going on without them" }
    } catch (_: RejectedExecutionException) {
      LOG.warn { "Could not wait for the jobs of $key, the queue is full; going on without them" }
    }
  }

  private fun workerFor(hash: Int): ThreadPoolExecutor {
    return workers[(hash and Int.MAX_VALUE) % workers.size]
  }

  private fun runSafely(job: () -> Unit) {
    try {
      job()
    } catch (e: Throwable) {
      if (e.isFatal()) throw e
      LOG.error(e) { "Async job failed: ${e.message}" }
    }
  }

  @PreDestroy
  fun shutdown() {
    workers.forEach { it.shutdown() }
    workers.forEach {
      try {
        if (!it.awaitTermination(5, TimeUnit.SECONDS)) it.shutdownNow()
      } catch (_: InterruptedException) {
        it.shutdownNow()
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private val ON_WORKER = ThreadLocal.withInitial { false }
  }
}
