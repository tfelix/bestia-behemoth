package net.bestia.zone.navigation

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * What pathfinding costs and how often it says no. The local tier shares [NavigationConfig.localPathfindsPerTick]
 * searches a tick among every creature in the zone, and a refused search is invisible to the creature (it walks
 * greedily instead), so without these the budget being too small shows only as slightly worse walking.
 *
 * Recorded on the tick thread. The default registry serves tests that build the services by hand.
 */
@Component
class NavigationMetrics(private val registry: MeterRegistry = SimpleMeterRegistry()) {

  /** How a local search ended. */
  enum class Local {
    /** A path was found. */
    FOUND,

    /** A search ran and found nothing: no route, or the expansion limit. */
    NO_ROUTE,

    /** The tick's search budget was spent, so nothing ran. */
    REFUSED_BUDGET,

    /** The target is farther than the local tier is asked about. */
    TOO_FAR,

    /** The target column is not walkable or not resident. Costs a search from the budget. */
    GOAL_UNWALKABLE,
  }

  /** How asking the macro tier for a route ended. */
  enum class Macro {
    PLANNED, NO_GRAPH, NO_START, NO_GOAL, SAME_NODE, NO_ROUTE
  }

  private val local = Local.entries.associateWith { outcome ->
    Counter.builder("zone.nav.local.searches")
      .tag("result", outcome.name.lowercase())
      .description("Local path searches by how they ended; refused_budget ones never ran")
      .register(registry)
  }

  private val localDuration = Timer.builder("zone.nav.local.duration")
    .description("Time one local A* search took")
    .serviceLevelObjectives(Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(25))
    .register(registry)

  private val localBudget = DistributionSummary.builder("zone.nav.local.budget.spent")
    .description("Searches charged to the local budget in one tick; its limit is navigation.local-pathfinds-per-tick")
    .register(registry)

  private val steps = listOf("moved", "blocked").associateWith { result ->
    Counter.builder("zone.nav.local.steps")
      .tag("result", result)
      .description("Greedy single steps, which stand in for a path the budget refused")
      .register(registry)
  }

  private val macro = Macro.entries.associateWith { outcome ->
    Counter.builder("zone.nav.macro.routes")
      .tag("result", outcome.name.lowercase())
      .description("Macro route requests by how they ended")
      .register(registry)
  }

  private val revalidated = Counter.builder("zone.nav.macro.revalidated")
    .description("Macro edges re-tested against the ground after a terrain edit")
    .register(registry)

  private val pendingRevalidations = AtomicInteger()

  init {
    Gauge.builder("zone.nav.macro.pending.revalidations", pendingRevalidations) { it.get().toDouble() }
      .description("Macro edges waiting to be re-tested")
      .register(registry)
  }

  fun local(outcome: Local) {
    local.getValue(outcome).increment()
  }

  /** Runs a search and times it. */
  fun <T> searched(block: () -> T): T {
    val started = System.nanoTime()
    try {
      return block()
    } finally {
      localDuration.record(System.nanoTime() - started, TimeUnit.NANOSECONDS)
    }
  }

  /** Called once a tick with what that tick's searches cost. */
  fun localBudgetSpent(searches: Int) {
    localBudget.record(searches.toDouble())
  }

  fun step(moved: Boolean) {
    steps.getValue(if (moved) "moved" else "blocked").increment()
  }

  fun macro(outcome: Macro) {
    macro.getValue(outcome).increment()
  }

  fun revalidated(edges: Int) {
    if (edges > 0) revalidated.increment(edges.toDouble())
  }

  fun pendingRevalidations(pending: Int) {
    pendingRevalidations.set(pending)
  }
}
