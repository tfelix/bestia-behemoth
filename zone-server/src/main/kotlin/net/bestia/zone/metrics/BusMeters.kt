package net.bestia.zone.metrics

import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.FunctionTimer
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import net.bestia.bus.contract.ZoneQuery
import net.bestia.bus.valkey.RequestServerStats
import net.bestia.zone.companion.BusRequestServer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * The questions the api-service asks this zone over the bus. The server is started once the zone is ready and is
 * absent when the bus is off, so each reading goes through it when scraped and reads zero when there is none.
 */
@Component
class BusMeters(private val server: ObjectProvider<BusRequestServer>) : MeterBinder {

  private fun stats(): RequestServerStats? {
    return server.ifAvailable?.stats
  }

  override fun bindTo(registry: MeterRegistry) {
    val outcomes: Map<String, () -> Long> = mapOf(
      "received" to { stats()?.received?.sum() ?: 0L },
      "unreadable" to { stats()?.unreadable?.sum() ?: 0L },
      "busy" to { server.ifAvailable?.busy ?: 0L },
      "expired" to { stats()?.expired?.sum() ?: 0L },
      "replayed" to { stats()?.replayed?.sum() ?: 0L },
      "failed" to { stats()?.failed?.sum() ?: 0L },
    )

    for ((outcome, count) in outcomes) {
      FunctionCounter.builder("zone.bus.requests", this) { count().toDouble() }
        .tag("outcome", outcome)
        .description(
          "Requests from the api-service by what happened: received, unreadable (dropped), busy (over the in-flight " +
            "limit), expired (the caller had given up), replayed (answered from an earlier reply), failed (the handler threw)"
        )
        .register(registry)
    }

    Gauge.builder("zone.bus.inflight", this) { (server.ifAvailable?.inFlight ?: 0).toDouble() }
      .description("Requests being answered right now; at the limit the next one is answered busy")
      .register(registry)

    for (query in queries(ZoneQuery::class)) {
      val name = query.simpleName ?: continue

      FunctionTimer.builder(
        "zone.bus.request.duration", this,
        { stats()?.of(name)?.count ?: 0L },
        { (stats()?.of(name)?.totalNanos ?: 0L).toDouble() },
        TimeUnit.NANOSECONDS
      )
        .tag("query", name)
        .description("Time the handler took, by the kind of question")
        .register(registry)
    }
  }

  /** Every question the contract has, found rather than listed, so a new one has its series from the start. */
  private fun queries(type: KClass<*>): List<KClass<*>> {
    val inner = type.sealedSubclasses
    return if (inner.isEmpty()) listOf(type) else inner.flatMap { queries(it) }
  }
}
