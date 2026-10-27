package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos

/**
 * The chunks somebody standing at a chunk should have around them: what a client is offered, and what a
 * simulation anchor holds resident.
 */
class ViewVolume(
  private val chunkService: ChunkService,
  private val settings: ChunkStreamConfig
) {

  /**
   * The chunks a player at [anchor] should hold.
   *
   * Horizontally a square of `viewRadiusChunks` around them. Vertically, the slabs that hold a *surface* in
   * each column - the generated terrain, the sea, cave passages, and whatever anybody has dug into, which
   * [ChunkService.surfaceSlabsOf] answers as one set - clipped to `viewRadiusChunksVertical` around the
   * player's own slab, plus that slab itself:
   *
   * ```
   * kept(column)  = (surfaces(column) ∩ [anchor.z - v, anchor.z + v]) ∪ { anchor.z }
   * slabs(column) = kept ∪ { z - 1 | z ∈ kept, z - 1 ∈ surfaces(column) }
   * ```
   *
   * ### Why the surface term is a set and not a span
   *
   * A chunk is 256 m tall, so one slab almost always contains a whole column's surface - but not the
   * *seabed's* and the *sea's* at once. Deep ocean puts them four slabs apart, and everything between is
   * solid water that the client meshes to nothing. Subscribing to the span rather than to its two ends is
   * what made a login in the ocean margin offer 726 chunks where 121 was the answer.
   *
   * ### Why it is clipped
   *
   * A view volume wants bounding in z for the same reason as in x and y, and without it a coastal column
   * offers the seabed a kilometre below a player who cannot see it. The clip is what keeps the count
   * proportional to what is on screen rather than to how deep the water happens to be.
   *
   * ### Why the player's own slab survives the clip
   *
   * They must have ground under them on the first tick even where the surface rule disagrees - standing on a
   * built platform, or in a cave, or simply at an elevation the heightfield knows nothing about. It replaces
   * an unconditional `anchor.z ± 1`, which tripled the count to buy the same guarantee.
   *
   * ### Why the clip may be exceeded by one slab downward, and why the order matters
   *
   * Both are [ChunkCoords.offeredSlabs]'s to explain. In short: a slab kept while the slab it draws its floor
   * against was clipped away offers something that draws nothing, and insertion order here becomes send order,
   * so a floor has to go out before what draws against it.
   */
  fun chunksAround(anchor: ChunkPos, budget: SlabBudget): Desired {
    val vertical = settings.viewRadiusChunksVertical
    val desired = LinkedHashSet<ChunkPos>()
    var complete = true

    for ((dx, dy) in viewOffsets) {
      val column = ChunkPos(anchor.x + dx, anchor.y + dy, anchor.z)
      val isAnchorColumn = dx == 0 && dy == 0

      // The player's own column is never deferred: they must have ground under them on the first tick, whatever
      // the budget says. Everywhere else waits its turn.
      // The player's own column is the one answer that cannot wait for a worker. Every other column is only
      // asked for here and offered once it has come back, which a later tick's manifest picks up.
      val slabs = chunkService.cachedSlabsOf(column)
        ?: if (isAnchorColumn) {
          chunkService.surfaceSlabsOf(column)
        } else {
          // A column already on its way costs nothing more, so it must not spend the budget again every tick
          // and starve the farther columns that have not been asked for yet.
          if (!chunkService.slabsInFlight(column) && budget.trySpend()) chunkService.requestSurfaceSlabs(column)
          chunkService.cachedSlabsOf(column)
        }

      if (slabs == null) {
        complete = false
        continue
      }

      ChunkCoords.offeredSlabs(slabs, anchor.z, vertical)
        .forEach { desired.add(chunkService.normalise(ChunkPos(column.x, column.y, it))) }
    }

    return Desired(desired, complete)
  }

  /** [complete] is false when the budget left a column of the view uncosted. */
  data class Desired(val chunks: Set<ChunkPos>, val complete: Boolean)

  /**
   * The view's column offsets, nearest first, so the send queue is already in the order a player wants it -
   * and so a budget that runs out spends what it had on the ground closest to them. The same for every player.
   */
  private val viewOffsets: List<Pair<Int, Int>> = run {
    val radius = settings.viewRadiusChunks
    (-radius..radius).flatMap { dy -> (-radius..radius).map { dx -> dx to dy } }
      .sortedBy { (dx, dy) -> dx * dx + dy * dy }
  }

  /**
   * The normalised horizontal columns within [radius] chunks of [anchor], ignoring vertical slabs.
   *
   * Exists to tell "outside the view" apart from "inside the view but not costed yet", which the desired set
   * alone cannot say. Normalised, so a view straddling the world seam does not read its own far half as out of
   * range.
   */
  fun columnsAround(anchor: ChunkPos, radius: Int): Set<Pair<Int, Int>> {
    val columns = HashSet<Pair<Int, Int>>((2 * radius + 1) * (2 * radius + 1))

    for (dy in -radius..radius) {
      for (dx in -radius..radius) {
        val column = chunkService.normalise(ChunkPos(anchor.x + dx, anchor.y + dy, anchor.z))
        columns.add(column.x to column.y)
      }
    }

    return columns
  }
}
