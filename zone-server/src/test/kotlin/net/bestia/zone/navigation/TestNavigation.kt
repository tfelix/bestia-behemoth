package net.bestia.zone.navigation

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.NavGraph
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.graph.MacroGraphService
import net.bestia.zone.navigation.graph.NavWorldSource
import net.bestia.zone.navigation.local.LocalPathfindingService
import net.bestia.zone.navigation.local.LocalWalkQuery
import net.bestia.zone.navigation.profile.MovementProfileRegistry

/**
 * Navigation wired up for a test, without a generated world.
 *
 * The seams exist precisely so this is possible: an AI test that asks whether a creature chases or flees has no
 * business generating terrain to find out, and before there was a [LocalWalkQuery] to substitute it would have
 * had to.
 */
object TestNavigation {

  /**
   * Ground that is flat, walkable and everywhere.
   *
   * The right default for behaviour tests: they assert about decisions, and terrain that refuses steps would
   * make every one of them fail for a reason unrelated to what it is testing. Pass [walkable] to carve
   * obstacles when the terrain *is* the point.
   */
  fun flatGround(walkable: (Vec3L) -> Boolean = { true }): LocalWalkQuery = object : LocalWalkQuery {
    override fun canStep(from: Vec3L, to: Vec3L) = walkable(to)
    override fun surfaceAt(position: Vec3L) = if (walkable(position)) 0L else null
    override fun isResident(position: Vec3L) = true
  }

  /** A world with no macro graph in it, for tests that only exercise local movement. */
  fun noMacroGraph(): NavWorldSource = object : NavWorldSource {
    override val isReady get() = false
    override fun navGraph() = NavGraph.EMPTY
    override fun place(metresX: Double, metresY: Double) = Vec3L(metresX.toLong(), metresY.toLong(), 0L)
    override fun chunkAt(position: Vec3L): ChunkPos? = null
    override fun onChunkChanged(handler: (ChunkPos) -> Unit) = Unit
  }

  /**
   * A ready [NavigationService] over the given ground.
   *
   * The local budget is reset per call rather than by the maintenance system, because a test drives the world
   * by hand and would otherwise exhaust one tick's worth of searches and silently stop moving.
   */
  fun service(
    walkQuery: LocalWalkQuery = flatGround(),
    worldSource: NavWorldSource = noMacroGraph(),
    config: NavigationConfig = NavigationConfig(localPathfindsPerTick = Int.MAX_VALUE)
  ): NavigationService {
    val local = LocalPathfindingService(walkQuery, config)
    val macro = MacroGraphService(worldSource, walkQuery, config)
    val profiles = MovementProfileRegistry().apply { load() }

    return NavigationService(macro, local, profiles, config)
  }
}
