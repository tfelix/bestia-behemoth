package net.bestia.zone.metrics

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.exporter.httpserver.HTTPServer
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component

/**
 * Serves the meters to Prometheus: `/metrics` for the scrape and `/-/healthy` for a liveness probe. The zone runs
 * no Spring web server, so this is the Prometheus client's own exporter, a JDK HTTP server on a few threads of
 * its own that only ever read the registry.
 *
 * Started with the context rather than when it is ready, so a scrape already sees a world being generated.
 *
 * The registry is optional because `@SpringBootTest` switches Prometheus export off; the tests serve nothing.
 */
@Component
class PrometheusEndpoint(
  registry: PrometheusMeterRegistry?,
  properties: MetricsProperties,
) {

  private val server: HTTPServer? = if (properties.port < 0) {
    null
  } else {
    checkNotNull(registry) { "metrics.port is set, but Prometheus export is off, so there is nothing to serve" }

    HTTPServer.builder()
      .hostname(properties.address)
      .port(properties.port)
      .registry(registry.prometheusRegistry)
      .buildAndStart()
      .also { LOG.info { "Serving metrics on ${properties.address}:${it.port}" } }
  }

  /** The port it listens on, or null when it serves nothing. */
  val port: Int?
    get() {
      return server?.port
    }

  @PreDestroy
  fun stop() {
    server?.close()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
