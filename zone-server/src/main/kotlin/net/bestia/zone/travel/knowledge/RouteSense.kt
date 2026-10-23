package net.bestia.zone.travel.knowledge

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.navigation.graph.MacroGraphService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.vicinity.ChunkColumn
import net.bestia.zone.world.vicinity.VicinityIndex
import net.bestia.zone.world.vicinity.VicinitySense
import org.springframework.stereotype.Component

/**
 * Learns the state of the built routes - roads, bridges, gates - that a traveller comes near, and of those
 * that change while it is near. This is the only way a [Knower] learns that a route is blocked by looking.
 */
@Component
class RouteSense(
  private val macroGraph: MacroGraphService,
  private val vicinity: VicinityIndex,
  private val knowledge: RouteKnowledge,
) : VicinitySense {

  override val reads: ComponentClassSet = Knower.READS

  /** Changed since the last tick, to be shown to whoever is near them then. */
  private val changedEdges = LinkedHashSet<Int>()

  init {
    macroGraph.onEdgeChanged { changedEdges.add(it) }
  }

  override fun onEntered(world: World, entity: EntityId, columns: Collection<ChunkColumn>) {
    val knower = Knower.of(world, entity)

    for (column in columns) {
      macroGraph.structuralEdgesInColumn(column.x, column.y).forEach { see(knower, it) }
    }
  }

  override fun afterUpdate(world: World) {
    for (edge in changedEdges) {
      macroGraph.forEachColumnOf(edge) { x, y ->
        vicinity.sensorsIn(ChunkColumn(x, y)).forEach { see(Knower.of(world, it), edge) }
      }
    }
    changedEdges.clear()

    if (world.tickCount % FORGET_EVERY_TICKS == 0L) forgetCreaturesThatAreGone(world)
  }

  private fun see(knower: Knower, edge: Int) {
    knowledge.observe(knower, edge, macroGraph.isBlocked(edge))
  }

  /** A creature's memory dies with it; a player's is kept for its master. */
  private fun forgetCreaturesThatAreGone(world: World) {
    val gone = knowledge.knowers().filter { it is Knower.Creature && !world.isAlive(it.entityId) }
    gone.forEach { knowledge.forget(it) }
  }

  private companion object {
    const val FORGET_EVERY_TICKS = 200L
  }
}
