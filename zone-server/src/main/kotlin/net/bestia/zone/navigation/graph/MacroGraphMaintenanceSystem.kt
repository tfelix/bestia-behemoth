package net.bestia.zone.navigation.graph

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.local.LocalPathfindingService
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps the macro graph honest, a few edges at a time.
 *
 * Two jobs, both cheap and both on a schedule rather than every tick:
 *
 * 1. Re-test the edges a chunk edit put in doubt. Every few seconds, bounded per pass - a bridge falling is a
 *    rare discrete event, so checking at the rate voxels change would be checking a hundred times more often
 *    than anything happens.
 * 2. Hand the local pathfinder a fresh per-tick budget.
 *
 * The second is why this runs [Schedule.EveryTick] while the first does not: budgets are per tick by
 * definition, and giving the two separate systems would mean two beans that must agree about ordering. The
 * revalidation pass keeps its own timer instead.
 *
 * Ordered after chunk streaming, so an edit made this tick is queued before this looks at the queue.
 */
@SpringComponent
@Order(46)
class MacroGraphMaintenanceSystem(
  private val macroGraph: MacroGraphService,
  private val localPathfinding: LocalPathfindingService,
  private val config: NavigationConfig
) : System {

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = emptySet()

  private var secondsSinceRevalidation = 0f

  override fun update(world: World, deltaTime: Float) {
    localPathfinding.resetBudget()

    macroGraph.ensureLoaded()
    if (!macroGraph.isReady) return

    secondsSinceRevalidation += deltaTime
    if (secondsSinceRevalidation < config.macroRevalidateSeconds) return
    secondsSinceRevalidation = 0f

    val pending = macroGraph.pendingRevalidations
    if (pending == 0) return

    val done = macroGraph.revalidate()
    LOG.debug { "Revalidated $done of $pending stale macro edges" }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
