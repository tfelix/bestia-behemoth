package net.bestia.zone.ecs.core

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Generic off-tick-thread job runner for [System]s that need to kick off fast, immediate work
 * (typically a DB write) without blocking the ECS tick thread and without waiting for a
 * periodic sync cycle (see [net.bestia.zone.ecs.persistence.EntityPersistenceService], which is
 * fine for routine snapshotting but too slow for "this must be durable right away" writes like
 * granting a looted item). Inject this directly into a system, the same way systems already
 * inject other Spring services (e.g. `ItemRepository`).
 *
 * Client traffic deliberately does **not** come through here: [net.bestia.zone.ecs.ZoneEngine] keeps its own
 * pool for sends, because a position update queued behind one of the blocking database writes below is
 * rubberbanding.
 *
 * ### Ordering
 * A job submitted with a [submit] `key` is guaranteed to never run concurrently with another job
 * sharing that key, and runs strictly in submission order relative to it (one single-threaded
 * worker per key, picked by hash). This matters whenever a job does a read-modify-write against
 * shared state - e.g. two loots racing to persist the same master's DB inventory row would
 * otherwise be able to interleave into a lost update. Jobs with different keys may run fully in
 * parallel. Always key by a stable domain id (e.g. a masterId or accountId), never by a transient
 * ECS entity id. Use the keyless [submit] overload only for jobs with no ordering requirement
 * against anything else. That last rule is about read-modify-write, which is what this pool is for; a pool
 * whose jobs only order a message stream may legitimately key by whatever names the stream.
 */
@Service
class AsyncJobExecutor(
  workerCount: Int = 4,
) {
  private val workers: List<ExecutorService> = List(workerCount) { i ->
    Executors.newSingleThreadExecutor { r -> Thread(r, "zone-async-job-$i") }
  }

  private val roundRobin = AtomicInteger(0)

  /** Runs [job] on a background worker, keeping jobs sharing [key] strictly ordered. */
  fun submit(key: Any, job: () -> Unit) {
    workerFor(key.hashCode()).submit { runSafely(job) }
  }

  /** Runs [job] on a background worker with no ordering guarantee against any other job. */
  fun submit(job: () -> Unit) {
    workerFor(roundRobin.getAndIncrement()).submit { runSafely(job) }
  }

  private fun workerFor(hash: Int): ExecutorService = workers[(hash and Int.MAX_VALUE) % workers.size]

  private fun runSafely(job: () -> Unit) {
    try {
      job()
    } catch (e: Exception) {
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
  }
}
