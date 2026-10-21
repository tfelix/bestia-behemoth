package net.bestia.zone.metrics

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Prometheus scrapes the zone over plain HTTP, though the zone runs no web server. */
class PrometheusEndpointTest {

  private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
  private val endpoints = mutableListOf<PrometheusEndpoint>()
  private val http = HttpClient.newHttpClient()

  @AfterEach
  fun stop() {
    endpoints.forEach { it.stop() }
  }

  @Test
  fun `a scrape returns the zone's meters`() {
    registry.counter("zone.test.scrapes").increment()
    val port = start(MetricsProperties(port = 0)).port!!

    val response = get(port, "/metrics")

    assertEquals(200, response.statusCode())
    assertTrue(response.body().contains("zone_test_scrapes_total 1.0"), response.body())
  }

  @Test
  fun `a liveness probe is answered`() {
    val port = start(MetricsProperties(port = 0)).port!!

    assertEquals(200, get(port, "/-/healthy").statusCode())
  }

  @Test
  fun `a negative port serves nothing`() {
    assertNull(start(MetricsProperties(port = -1)).port)
  }

  private fun start(properties: MetricsProperties): PrometheusEndpoint {
    return PrometheusEndpoint(registry, properties).also { endpoints += it }
  }

  private fun get(port: Int, path: String): HttpResponse<String> {
    val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).build()
    return http.send(request, HttpResponse.BodyHandlers.ofString())
  }
}
