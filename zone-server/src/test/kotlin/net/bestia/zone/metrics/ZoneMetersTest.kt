package net.bestia.zone.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.cartography.tile.MapTileService
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.message.AccountInbox
import net.bestia.zone.message.TickOutbox
import net.bestia.zone.socket.ChannelRegistry
import net.bestia.zone.travel.TravelConfig
import net.bestia.zone.travel.knowledge.Knower
import net.bestia.zone.travel.knowledge.RouteKnowledge
import net.bestia.zone.world.anchor.AnchorResidencySystem
import net.bestia.zone.world.anchor.SimulationAnchorIndex
import net.bestia.zone.world.residency.ChunkResidency
import net.bestia.zone.world.stream.ChunkStreamInbox
import net.bestia.zone.world.vicinity.VicinityIndex
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZoneMetersTest {

  private class TickingSystem : System {
    override val phase = Phase.UPKEEP

    override fun update(world: World, deltaTime: Float) {}
  }

  private val world = testWorld(systems = listOf(TickingSystem()))
  private val jobs = AsyncJobExecutor(workerCount = 2)
  private val channels = mockk<ChannelRegistry> { every { connectedCount } returns 3 }
  private val inbox = AccountInbox(world, TickOutbox(mockk(relaxed = true)), provider(channels))
  private val mapTiles = mockk<MapTileService> { every { queuedRenders } returns 0 }
  private val anchorResidency = mockk<AnchorResidencySystem> { every { heldChunks } returns 27 }
  private val routeKnowledge = RouteKnowledge(TravelConfig())

  private val zoneMeters = ZoneMeters(
    world = world,
    inbox = inbox,
    jobs = jobs,
    sessions = ConnectionInfoService(),
    chunkInbox = ChunkStreamInbox(),
    mapTiles = mapTiles,
    residency = ChunkResidency(),
    anchors = SimulationAnchorIndex(),
    anchorResidency = anchorResidency,
    routeKnowledge = routeKnowledge,
    vicinity = VicinityIndex(),
    channels = provider(channels),
  )

  @AfterEach
  fun shutDown() {
    jobs.shutdown()
    inbox.shutdown()
  }

  @Test
  fun `queues and counts are read from their sources`() {
    val meters = SimpleMeterRegistry()
    zoneMeters.bindTo(meters)

    jobs.submit("k") { throw RuntimeException("boom") }
    jobs.awaitPending("k")

    assertEquals(3.0, meters.get("zone.players.connected").gauge().value())
    assertEquals(1.0, meters.get("zone.db.jobs.failed").functionCounter().count())
    assertEquals(1.0, meters.get("zone.db.job.duration").tag("stage", "run").functionTimer().count())
    assertEquals(2, meters.find("zone.db.jobs.pending").gauges().size, "one gauge per worker")
    assertEquals(3, meters.find("zone.chunk.inbox.pending").gauges().size)
  }

  @Test
  fun `world memory is counted in entries`() {
    val meters = SimpleMeterRegistry()
    zoneMeters.bindTo(meters)

    routeKnowledge.observe(Knower.Player(1L), 7, blocked = true)
    routeKnowledge.observe(Knower.Player(1L), 8, blocked = true)

    assertEquals(27.0, meters.get("zone.anchors.chunks.held").gauge().value())
    assertEquals(1.0, meters.get("zone.travel.knowers").gauge().value())
    assertEquals(2.0, meters.get("zone.travel.beliefs").gauge().value())
    assertEquals(0.0, meters.get("zone.vicinity.sensors").gauge().value())
  }

  @Test
  fun `every registered system gets a timer of its own`() {
    val meters = SimpleMeterRegistry()
    SystemMeters(meters, world).bind()

    world.tick(0.05f)

    val timer = meters.get("zone.system.duration").tags("system", "TickingSystem", "phase", "upkeep").functionTimer()
    assertEquals(1.0, timer.count())
  }

  /** Pins the names a dashboard queries. */
  @Test
  fun `the Prometheus names are the ones dashboards use`() {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    bindAll(prometheus)
    TickMetrics(prometheus, WorldRulesConfig(tickRate = 20)).recordTotal(1_000_000)

    val scrape = prometheus.scrape()

    for (name in listOf(
      "zone_tick_duration_seconds_count",
      "zone_db_jobs_failed_total",
      "zone_db_job_duration_seconds_count",
      "zone_inbox_pending",
      "zone_players_connected",
      "zone_system_duration_seconds_count",
    )) {
      assertTrue(scrape.contains(name), "$name is missing from the scrape")
    }
  }

  private fun bindAll(registry: MeterRegistry) {
    zoneMeters.bindTo(registry)
    SystemMeters(registry, world).bind()
  }

  private fun <T : Any> provider(value: T): ObjectProvider<T> {
    return mockk { every { ifAvailable } returns value }
  }
}
