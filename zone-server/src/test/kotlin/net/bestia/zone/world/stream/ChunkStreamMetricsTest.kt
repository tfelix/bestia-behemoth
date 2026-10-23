package net.bestia.zone.world.stream

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.metrics.StreamingMeters
import net.bestia.zone.world.stream.ChunkStreamMetrics.Allowance
import net.bestia.zone.world.stream.ChunkStreamMetrics.Kind
import net.bestia.zone.world.stream.ChunkStreamMetrics.Step
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkStreamMetricsTest {

  private val meters = SimpleMeterRegistry()
  private val metrics = ChunkStreamMetrics(meters)

  private fun counter(name: String, vararg tags: String): Double {
    return meters.get(name).tags(*tags).counter().count()
  }

  private fun gauge(name: String): Double {
    return meters.get(name).gauge().value()
  }

  @Test
  fun `a step is timed whatever it returns, and even when it throws`() {
    assertEquals(7, metrics.timed(Step.CARVES) { 7 })
    runCatching { metrics.timed(Step.CARVES) { error("boom") } }

    assertEquals(2L, meters.get("zone.chunk.step.duration").tag("step", "carves").timer().count())
    assertEquals(0L, meters.get("zone.chunk.step.duration").tag("step", "requests").timer().count())
  }

  @Test
  fun `a budget is counted by what it paid for and by the ticks it ran out`() {
    metrics.spent(Allowance.SLAB, 4)
    metrics.spent(Allowance.SLAB, 0)
    metrics.exhausted(Allowance.SLAB)

    assertEquals(4.0, counter("zone.chunk.budget.spent", "budget", "slab"))
    assertEquals(1.0, counter("zone.chunk.budget.exhausted", "budget", "slab"))
    assertEquals(0.0, counter("zone.chunk.budget.exhausted", "budget", "encode"))
  }

  @Test
  fun `chunks sent are counted by kind with their bytes`() {
    metrics.sent(Kind.PATCH, messages = 3, bytes = 120)
    metrics.sent(Kind.FULL, messages = 1, bytes = 900)

    assertEquals(3.0, counter("zone.chunk.sent", "kind", "patch"))
    assertEquals(120.0, counter("zone.chunk.sent.bytes", "kind", "patch"))
    assertEquals(900.0, counter("zone.chunk.sent.bytes", "kind", "full"))
  }

  @Test
  fun `a refused manifest counts as refused and offers nothing`() {
    metrics.manifest(sent = false)
    metrics.manifest(sent = true, added = 10, removed = 2)

    assertEquals(1.0, counter("zone.chunk.manifests", "result", "refused"))
    assertEquals(1.0, counter("zone.chunk.manifests", "result", "sent"))
    assertEquals(10.0, counter("zone.chunk.manifest.chunks", "change", "added"))
    assertEquals(2.0, counter("zone.chunk.manifest.chunks", "change", "removed"))
  }

  @Test
  fun `the age of the oldest unsaved edit grows until the journal is empty`() {
    metrics.journal(pending = 3, since = System.nanoTime() - TimeUnit.SECONDS.toNanos(5))

    assertEquals(3.0, gauge("zone.chunk.journal.pending"))
    assertTrue(gauge("zone.chunk.journal.oldest.age") >= 5.0)

    metrics.journal(pending = 0, since = 0)

    assertEquals(0.0, gauge("zone.chunk.journal.oldest.age"))
  }

  @Test
  fun `a worker job is timed by whether it generated the chunk`() {
    metrics.worked(generated = true) { }
    metrics.worked(generated = false) { }
    metrics.worked(generated = false) { }

    assertEquals(1L, meters.get("zone.chunk.worker.duration").tag("kind", "generate").timer().count())
    assertEquals(2L, meters.get("zone.chunk.worker.duration").tag("kind", "encode").timer().count())
  }

  @Test
  fun `requests are counted by outcome`() {
    metrics.requests(queued = 5)
    metrics.requests(deferred = 3)
    metrics.requests(unannounced = 1)

    assertEquals(5.0, counter("zone.chunk.requests", "outcome", "queued"))
    assertEquals(3.0, counter("zone.chunk.requests", "outcome", "deferred"))
    assertEquals(1.0, counter("zone.chunk.requests", "outcome", "unannounced"))
  }

}

/** The parts of streaming that keep their own counts, read when scraped. */
class StreamingMetersTest {
    private val meters = SimpleMeterRegistry()
    private val pool = Executors.newFixedThreadPool(1) as ThreadPoolExecutor
    private val workers = ChunkWorkers(pool)
    private val inbox = ChunkStreamInbox()

    @AfterEach
    fun shutDown() {
      pool.shutdownNow()
    }

    @Test
    fun `workers report what is queued and what is running`() {
      StreamingMeters(workers, inbox).bindTo(meters)
      val release = CountDownLatch(1)
      val started = CountDownLatch(1)

      workers.submit({ started.countDown(); release.await(5, TimeUnit.SECONDS) }, { })
      workers.submit({ }, { })
      started.await(5, TimeUnit.SECONDS)

      assertEquals(1.0, meters.get("zone.chunk.workers").tag("state", "active").gauge().value())
      assertEquals(1.0, meters.get("zone.chunk.workers").tag("state", "queued").gauge().value())
      release.countDown()
    }

    @Test
    fun `requests over the per-account cap are counted as dropped chunks`() {
      StreamingMeters(workers, inbox).bindTo(meters)

      inbox.offerRequest(ChunkStreamInbox.Request(1L, List(3000) { ChunkPos(it, 0, 0) }))

      assertEquals(3000.0, meters.get("zone.chunk.inbox.dropped").tag("kind", "requests").functionCounter().count())
      assertEquals(0.0, meters.get("zone.chunk.inbox.dropped").tag("kind", "carves").functionCounter().count())
    }
}
