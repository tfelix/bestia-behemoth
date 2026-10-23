package net.bestia.zone.metrics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusNamingConvention
import net.bestia.zone.scenarios.BestiaNoSocketScenario
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Grafana dashboards in `observability/grafana/dashboards` are JSON nobody compiles, so a renamed meter would
 * leave a panel quietly empty. This reads every `zone_*` series the dashboards query and asks the booted zone's
 * registry whether such a meter exists.
 *
 * Only `zone_*` names are checked here: `login_*` and `api_*` belong to the other services' own tests, and the
 * `jvm_*`, `hikaricp_*` and `http_server_requests_*` families come from Spring Boot's binders.
 */
class DashboardMetricsTest : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var registry: MeterRegistry

  @Test
  fun `every dashboard is valid JSON with a unique uid and title`() {
    val dashboards = dashboards()

    assertTrue(dashboards.isNotEmpty(), "no dashboards under $DASHBOARD_DIR")
    assertEquals(dashboards.size, dashboards.map { it.second["uid"].asText() }.toSet().size, "dashboard uids repeat")
    assertEquals(dashboards.size, dashboards.map { it.second["title"].asText() }.toSet().size, "dashboard titles repeat")
  }

  @Test
  fun `every zone series a dashboard queries exists in the registry`() {
    val known = prometheusNames() + LAZY_SERIES

    val missing = dashboards().flatMap { (file, dashboard) ->
      expressions(dashboard).flatMap { expr -> ZONE_SERIES.findAll(expr).map { it.value } }
        .filter { it !in known }
        .map { "$file: $it" }
    }.distinct()

    assertTrue(missing.isEmpty(), "dashboards query series the zone does not export:\n${missing.joinToString("\n")}")
  }

  /** What a scrape would call each meter, with every suffix its type can grow. */
  private fun prometheusNames(): Set<String> {
    val convention = PrometheusNamingConvention()

    return registry.meters.flatMap { meter ->
      val id = meter.id
      val base = convention.name(id.name, id.type, id.baseUnit).removeSuffix("_total")
      when (id.type) {
        Meter.Type.COUNTER -> listOf("${base}_total")
        Meter.Type.GAUGE -> listOf(base)
        else -> listOf("${base}_count", "${base}_sum", "${base}_bucket", "${base}_max")
      }
    }.toSet()
  }

  private fun expressions(dashboard: JsonNode): List<String> {
    return dashboard["panels"].flatMap { panel ->
      panel["targets"]?.mapNotNull { it["expr"]?.asText() } ?: emptyList()
    } + dashboard["templating"]["list"].mapNotNull { it["definition"]?.asText() }
  }

  private fun dashboards(): List<Pair<String, JsonNode>> {
    val dir = Path.of(DASHBOARD_DIR)

    return Files.list(dir).use { files ->
      files.filter { it.extension == "json" }.sorted().map { it.name to MAPPER.readTree(it.toFile()) }.toList()
    }
  }

  private companion object {
    /** Gradle runs a module's tests with the module as the working directory. */
    const val DASHBOARD_DIR = "../observability/grafana/dashboards"

    val MAPPER = ObjectMapper()

    val ZONE_SERIES = Regex("""\bzone_[a-z0-9_]+""")

    /**
     * Series made the first time something happens, because their tag is a name from a list the zone does not
     * enumerate up front (message classes, goals, actions). A test of each class pins its name, so the dashboard
     * cannot drift from it: `ZoneEngineTest`, `AiMetricsTest` and `GameMetricsTest`.
     */
    val LAZY_SERIES = setOf(
      "zone_sync_messages_total", "zone_ai_goals_adopted_total", "zone_ai_actions_failed_total",
      "zone_status_effects_active", "zone_economy_shop_refusals_total", "zone_trade_ended_total",
      // Made once the world has loaded, because its record does not exist before.
      "zone_info",
    )
  }
}
