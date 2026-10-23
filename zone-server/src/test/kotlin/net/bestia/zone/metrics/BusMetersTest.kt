package net.bestia.zone.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import net.bestia.bus.valkey.RequestServerStats
import net.bestia.zone.companion.BusRequestServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BusMetersTest {

  private val meters = SimpleMeterRegistry()

  private fun provider(server: BusRequestServer?): ObjectProvider<BusRequestServer> {
    return mockk { every { ifAvailable } returns server }
  }

  private fun requests(outcome: String): Double {
    return meters.get("zone.bus.requests").tag("outcome", outcome).functionCounter().count()
  }

  @Test
  fun `what the server counted is read when scraped`() {
    val counted = RequestServerStats().also {
      it.received.add(5)
      it.expired.increment()
      it.replayed.add(2)
    }
    val server = mockk<BusRequestServer> {
      every { stats } returns counted
      every { busy } returns 4L
      every { inFlight } returns 3
    }

    BusMeters(provider(server)).bindTo(meters)

    assertEquals(5.0, requests("received"))
    assertEquals(1.0, requests("expired"))
    assertEquals(2.0, requests("replayed"))
    assertEquals(4.0, requests("busy"))
    assertEquals(3.0, meters.get("zone.bus.inflight").gauge().value())
  }

  @Test
  fun `a zone with the bus off reads zero rather than failing`() {
    BusMeters(provider(null)).bindTo(meters)

    assertEquals(0.0, requests("received"))
    assertEquals(0.0, meters.get("zone.bus.inflight").gauge().value())
  }

  @Test
  fun `every question the contract has gets a timer, found rather than listed`() {
    BusMeters(provider(null)).bindTo(meters)

    val queries = meters.meters.filter { it.id.name == "zone.bus.request.duration" }.map { it.id.getTag("query") }

    assertTrue("MapTile" in queries && "Catalog" in queries && "ListMasters" in queries, queries.toString())
    assertEquals(0.0, meters.get("zone.bus.request.duration").tag("query", "MapTile").functionTimer().totalTime(TimeUnit.NANOSECONDS))
  }
}
