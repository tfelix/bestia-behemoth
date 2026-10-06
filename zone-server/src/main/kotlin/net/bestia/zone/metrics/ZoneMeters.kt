package net.bestia.zone.metrics

import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.FunctionTimer
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import net.bestia.zone.cartography.tile.MapTileService
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.AccountInbox
import net.bestia.zone.socket.ChannelRegistry
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * The zone's queues and counts, read when the metrics are scraped. The sources keep plain counters, so
 * nothing on the tick pays for a meter.
 */
@Component
class ZoneMeters(
  private val world: EcsWorld,
  private val inbox: AccountInbox,
  private val jobs: AsyncJobExecutor,
  private val sessions: ConnectionInfoService,
  private val chunkInbox: ChunkStreamInbox,
  private val mapTiles: MapTileService,
  // Absent under the `no-socket` profile.
  private val channels: ObjectProvider<ChannelRegistry>,
) : MeterBinder {

  override fun bindTo(registry: MeterRegistry) {
    bindTick(registry)
    bindInbox(registry)
    bindJobs(registry)
    bindPlayers(registry)
    bindStreaming(registry)
  }

  private fun bindTick(registry: MeterRegistry) {
    Gauge.builder("zone.entities", world) { it.entityCount.toDouble() }
      .description("Entities in the world")
      .register(registry)
    Gauge.builder("zone.tick.posted.pending", world) { it.pendingPosts.toDouble() }
      .description("Tick-lane messages, leases and other work waiting for the tick thread")
      .register(registry)
  }

  private fun bindInbox(registry: MeterRegistry) {
    Gauge.builder("zone.inbox.pending", inbox) { it.pendingMessages.toDouble() }
      .description("Client messages that arrived and have not started")
      .register(registry)
    Gauge.builder("zone.inbox.io.queued", inbox) { it.ioQueued.toDouble() }
      .description("IO-lane messages waiting for a free IO thread")
      .register(registry)
    FunctionCounter.builder("zone.inbox.overflows", inbox) { it.overflowCount.toDouble() }
      .description("Connections closed because their inbox was full")
      .register(registry)
  }

  private fun bindJobs(registry: MeterRegistry) {
    for (worker in 0 until jobs.workerCount) {
      Gauge.builder("zone.db.jobs.pending", jobs) { it.pendingJobsOf(worker).toDouble() }
        .tag("worker", worker.toString())
        .description("DB jobs queued on one worker")
        .register(registry)
    }
    FunctionCounter.builder("zone.db.jobs.rejected", jobs) { it.rejectedJobs.toDouble() }
      .description("DB jobs dropped because their worker's queue was full")
      .register(registry)
    FunctionCounter.builder("zone.db.jobs.failed", jobs) { it.failedJobs.toDouble() }
      .description("DB jobs that threw")
      .register(registry)
    jobTimer(registry, "wait") { it.jobWaitNanos }
    jobTimer(registry, "run") { it.jobRunNanos }
  }

  private fun jobTimer(registry: MeterRegistry, stage: String, nanos: (AsyncJobExecutor) -> Long) {
    FunctionTimer.builder(
      "zone.db.job.duration", jobs,
      { it.finishedJobs },
      { nanos(it).toDouble() },
      TimeUnit.NANOSECONDS
    )
      .tag("stage", stage)
      .description("How long DB jobs waited in their queue, and how long they ran")
      .register(registry)
  }

  private fun bindPlayers(registry: MeterRegistry) {
    Gauge.builder("zone.players.connected", channels) { it.ifAvailable?.connectedCount?.toDouble() ?: 0.0 }
      .description("Accounts with a logged-in connection")
      .register(registry)
    Gauge.builder("zone.players.sessions", sessions) { it.sessionCount.toDouble() }
      .description("Accounts holding a session")
      .register(registry)
  }

  private fun bindStreaming(registry: MeterRegistry) {
    Gauge.builder("zone.chunk.inbox.pending", chunkInbox) { it.pendingRequests.toDouble() }
      .tag("kind", "requests")
      .description("Chunk requests, carves and teleports waiting for the tick")
      .register(registry)
    Gauge.builder("zone.chunk.inbox.pending", chunkInbox) { it.pendingCarves.toDouble() }
      .tag("kind", "carves")
      .register(registry)
    Gauge.builder("zone.chunk.inbox.pending", chunkInbox) { it.pendingTeleports.toDouble() }
      .tag("kind", "teleports")
      .register(registry)
    Gauge.builder("zone.map.renders.queued", mapTiles) { it.queuedRenders.toDouble() }
      .description("Map tiles waiting for a render thread")
      .register(registry)
  }
}
