package net.bestia.login.metrics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusNamingConvention
import net.bestia.login.scenario.BaseLoginScenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The login dashboard is hand-written JSON, so nothing but this test notices a panel querying a metric that was
 * renamed or never existed: it would simply stay empty. Every metric named in an `expr` has to exist in the booted
 * context, apart from the ones Boot's own binders own.
 */
class DashboardMetricsTest : BaseLoginScenario() {

  private val dashboard: JsonNode = ObjectMapper().readTree(Files.readString(DASHBOARD))

  @Test
  fun `the dashboard is the login one, provisioned from the Bestia folder by uid`() {
    assertEquals("bestia-login", dashboard.get("uid").asText())
    assertEquals("Login Server", dashboard.get("title").asText())
    assertEquals(
      listOf("application"),
      dashboard.get("templating").get("list").map { it.get("name").asText() }
    )
  }

  @Test
  fun `every metric a panel queries exists`() {
    val known = prometheusNames(meters)
    val queried = exprs().flatMap { metricNamesIn(it) }.toSortedSet()

    assertFalse(queried.isEmpty(), "found no metric names in the dashboard, so the extraction is broken")

    val missing = queried.filter { name -> name !in known && AUTO_BOUND.none { name.startsWith(it) } && name != "up" }
    assertTrue(missing.isEmpty(), "dashboard queries metrics the login server does not register: $missing")
  }

  @Test
  fun `the application variable is offered by a metric that exists`() {
    val query = dashboard.get("templating").get("list").first().get("definition").asText()
    val metric = Regex("""label_values\((\w+),""").find(query)!!.groupValues[1]

    assertTrue(metric in prometheusNames(meters), "$metric is not registered")
  }

  @Test
  fun `every panel queries Prometheus through the provisioned datasource`() {
    val datasources = dashboard.get("panels")
      .filter { it.has("targets") }
      .flatMap { panel -> panel.get("targets").map { it.get("datasource").get("uid").asText() } }
      .toSet()

    assertEquals(setOf("prometheus"), datasources)
  }

  @Test
  fun `the extraction reads names and leaves labels, ranges and functions alone`() {
    val names = metricNamesIn(
      """sum by (client, flow) (rate(login_grant_total{application="${'$'}application", result="ok"}[${'$'}__rate_interval])) / max(login_accounts)"""
    )

    assertEquals(setOf("login_grant_total", "login_accounts"), names)
  }

  private fun exprs(): List<String> {
    return dashboard.get("panels").filter { it.has("targets") }.flatMap { panel -> panel.get("targets").map { it.get("expr").asText() } }
  }

  /** The Prometheus series names a registry would export: counters gain `_total`, timers `_seconds` and its parts. */
  private fun prometheusNames(registry: MeterRegistry): Set<String> {
    val convention = PrometheusNamingConvention()

    return registry.meters.flatMap { meter ->
      val base = convention.name(meter.id.name, meter.id.type, meter.id.baseUnit)

      when (meter.id.type) {
        Meter.Type.COUNTER -> listOf(base.removeSuffix("_total") + "_total")
        Meter.Type.TIMER, Meter.Type.LONG_TASK_TIMER ->
          listOf("_count", "_sum", "_max", "_bucket").map { base + it }
        else -> listOf(base)
      }
    }.toSet()
  }

  private fun metricNamesIn(expr: String): Set<String> {
    val stripped = expr
      .replace(Regex("\"[^\"]*\""), "")
      .replace(Regex("\\{[^}]*}"), "")
      .replace(Regex("\\[[^\\]]*]"), "")
      .replace(Regex("\\b(by|without|on|ignoring)\\s*\\([^)]*\\)"), "")

    return Regex("[a-zA-Z_:][a-zA-Z0-9_:]*").findAll(stripped).map { it.value }.filter { it !in PROMQL_WORDS }.toSet()
  }

  private companion object {
    /** Where the dashboards live, relative to the module the tests run in. */
    val DASHBOARD: Path = Path.of("../observability/grafana/dashboards/login-server.json")

    /** Series owned by the auto-configured binders and not by this module. */
    val AUTO_BOUND = listOf("jvm_", "hikaricp_", "http_server_requests_", "process_", "system_")

    val PROMQL_WORDS = setOf(
      "sum", "avg", "min", "max", "count", "rate", "irate", "increase", "histogram_quantile", "clamp_min", "clamp_max",
      "abs", "round", "label_replace", "topk", "bottomk", "by", "without", "on", "ignoring", "group_left", "group_right",
      "and", "or", "unless", "bool", "offset", "sum_over_time", "avg_over_time", "max_over_time",
    )
  }
}
