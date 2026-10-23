package net.bestia.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusNamingConvention
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * Every metric the API dashboards graph exists in the running service, so a renamed or mistyped meter is a failing
 * test, not an empty panel. Metrics the auto-configured binders own are checked against a short list of prefixes.
 */
class DashboardMetricsTest : ApiScenarioTestBase() {

  @Autowired
  private lateinit var registry: MeterRegistry

  @Test
  fun `the dashboards graph only series the service exports`() {
    val dashboards = Files.list(DASHBOARDS).use { files -> files.filter { it.fileName.toString().startsWith("api-") && it.fileName.toString().endsWith(".json") }.toList() }
    assertTrue(dashboards.isNotEmpty(), "no api-*.json in $DASHBOARDS")

    val exported = exportedNames()
    val missing = dashboards.flatMap { dashboard ->
      queries(jacksonObjectMapper().readTree(dashboard.toFile()))
        .flatMap { metricNames(it) }
        .filter { name -> AUTO_BOUND.none { name.startsWith(it) } && name !in exported }
        .map { "${dashboard.fileName}: $it" }
    }.distinct()

    assertTrue(missing.isEmpty(), "Not exported by api-service: $missing")
  }

  @Test
  fun `the names are read out of a query the way Prometheus would`() {
    val expr = """histogram_quantile(0.95, sum by (le, query) (rate(api_zone_ask_seconds_bucket{application=~"${'$'}application",result!="ok"}[${'$'}__rate_interval]))) / on() group_left sum(api_bus_asks_total)"""

    assertTrue(metricNames(expr) == setOf("api_zone_ask_seconds_bucket", "api_bus_asks_total"), metricNames(expr).toString())
  }

  /** The names Prometheus shows for what is registered: the base name, `_total` on counters, `_count`/`_sum`/`_bucket` on timers. */
  private fun exportedNames(): Set<String> {
    val convention = PrometheusNamingConvention()

    return registry.meters.flatMap { meter ->
      val id = meter.id
      val base = convention.name(id.name, id.type, id.baseUnit)
      when (id.type) {
        Meter.Type.COUNTER -> listOf(base, "${base.removeSuffix("_total")}_total")
        Meter.Type.TIMER, Meter.Type.DISTRIBUTION_SUMMARY, Meter.Type.LONG_TASK_TIMER -> {
          val unit = if (id.type == Meter.Type.DISTRIBUTION_SUMMARY || base.endsWith("_seconds")) base else "${base}_seconds"
          listOf("_count", "_sum", "_max", "_bucket").map { unit + it }
        }

        else -> listOf(base)
      }
    }.toSet()
  }

  private fun queries(dashboard: JsonNode): List<String> {
    val panels = dashboard["panels"].flatMap { it["targets"] ?: emptyList() }.mapNotNull { it["expr"]?.asText() }
    val variables = dashboard["templating"]["list"].mapNotNull { it["definition"]?.asText() ?: it["query"]?.asText() }
    return panels + variables
  }

  /** Identifiers with an underscore that are not a function, a label or a grouping: the metric names of a PromQL expression. */
  private fun metricNames(expr: String): Set<String> {
    val bare = expr
      .replace(Regex(""""[^"]*""""), "")
      .replace(Regex("""\{[^}]*}"""), "")
      .replace(Regex("""\$\w+"""), "")
      .replace(Regex("""\b(by|without|on|ignoring|group_left|group_right)\s*\([^)]*\)"""), "")

    return Regex("""[a-zA-Z_:][a-zA-Z0-9_:]*(?![a-zA-Z0-9_:])(?!\s*\()""").findAll(bare).map { it.value }.filter { '_' in it && it !in KEYWORDS }.toSet()
  }

  private companion object {
    /** Where the dashboards live, relative to this module. */
    val DASHBOARDS: Path = Path.of("../observability/grafana/dashboards")

    /** Words PromQL spells with an underscore that are not metric names. */
    val KEYWORDS = setOf("group_left", "group_right")

    /** Series the auto-configured binders own: JVM, pool, Tomcat, HTTP server, process and host. */
    val AUTO_BOUND = listOf("jvm_", "hikaricp_", "http_server_requests_", "process_", "system_", "tomcat_")
  }
}
