package net.bestia.zone.metrics

import org.springframework.boot.context.properties.ConfigurationProperties

/** Where [PrometheusEndpoint] listens. A negative [port] serves nothing; 0 takes any free port. */
@ConfigurationProperties(prefix = "metrics")
data class MetricsProperties(
  val address: String = "127.0.0.1",
  val port: Int = 8092,
)
