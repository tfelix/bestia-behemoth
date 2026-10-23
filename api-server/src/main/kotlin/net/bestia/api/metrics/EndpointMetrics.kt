package net.bestia.api.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * What became of the requests for cached content: the map's tiles and overlays, and the emblems. Request counts and
 * latency per route template are `http.server.requests`; this adds how each one was answered, which that cannot see.
 */
@Component
class EndpointMetrics(registry: MeterRegistry) {

  enum class Outcome {
    /** The zone sent the content with this request. */
    SERVED_FROM_ZONE,

    /** The caller's own copy is current: a 304, no body. */
    CLIENT_304,

    /** The zone confirmed this service's copy, which went out as the body. */
    CACHE_REVALIDATED,

    /** The zone confirmed a copy this service had since let go, so it asked again for the bytes. */
    EVICTED_REFETCH,

    /** Answered from this service's memory without asking the zone. */
    CACHE_HIT,
  }

  private val cachedRoutes = ApiRoute.entries.filter { it.cached }

  private val outcomes = buildMap {
    for (route in cachedRoutes) {
      for (outcome in Outcome.entries) {
        put(
          route to outcome,
          Counter.builder("api.endpoint.outcomes")
            .description("How requests for cached content were answered")
            .tag("route", route.name.lowercase())
            .tag("outcome", outcome.name.lowercase())
            .register(registry),
        )
      }
    }
  }

  private val bodyBytes = cachedRoutes.associateWith {
    Counter.builder("api.endpoint.body")
      .description("Bytes of response body sent to callers")
      .baseUnit("bytes")
      .tag("route", it.name.lowercase())
      .register(registry)
  }

  fun outcome(route: ApiRoute, outcome: Outcome) {
    outcomes.getValue(route to outcome).increment()
  }

  fun bodySent(route: ApiRoute, bytes: Int) {
    bodyBytes.getValue(route).increment(bytes.toDouble())
  }
}
