package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.WalkableTile
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.local.LocalWalkQuery
import org.springframework.stereotype.Service

/**
 * Answers [LocalWalkQuery] from the derived walkability tiles.
 *
 * Thin for the reason [ChunkGroundHeight] is thin: everything the streaming layer knows about chunk
 * coordinates, voxel sizes and the position-unit convention stays on this side of the seam.
 *
 * Unlike the heightfield this reads `ChunkService.derived()`, and that is the whole point of it existing: the
 * derived tiles are built from *merged* voxels, so a doorway a player bricked up is not walkable and a hole
 * they dug reports its new floor. Movement that consulted only the heightfield would path through both.
 *
 * ### Two frames, and mixing them was this class's oldest bug
 *
 * A [Vec3L] is global: `z` is the voxel index whose zero is sea level. A [WalkableTile] is per **slab**, and
 * the surfaces it stores are heights *within* that slab, in `[0, chunkHeight)`. Every crossing between the two
 * has to add or subtract the slab's own base, and this class used to do neither - it localised every query at
 * `voxelZ = 0`, so it always read the slab covering 0 to 256 m, and then handed that slab's local surface
 * height back as though it were an elevation. The two mistakes cancel exactly once, for slab zero, which is
 * why terrain between sea level and 256 m worked and nothing above it did: at 1 283 m the query read bedrock
 * with no clearance, found no span, and returned `null` forever. Every caller then fell back to whatever the
 * client claimed its own height was.
 */
@Service
class ChunkWalkQuery(private val chunkService: ChunkService) : LocalWalkQuery {

  /**
   * A standable surface under a position: which slab holds it, which column, and how high inside that slab.
   *
   * [surface] is chunk-local, because that is the only frame [WalkableTile] speaks and the frame
   * `DerivedStore.canStep` compares two tiles in.
   */
  private class Footing(
    val chunk: ChunkPos,
    val localX: Int,
    val localY: Int,
    val surface: Double
  )

  override fun canStep(from: Vec3L, to: Vec3L): Boolean {
    if (!chunkService.isReady) return false

    val config = chunkService.config
    val derived = chunkService.derived()

    val origin = footingAt(from) ?: return false

    // The target column is resolved in the *origin's* slab rather than its own. `DerivedStore.canStep`
    // compares the two tiles' surface heights directly, and two tiles at different slabs measure from
    // different floors - so the frame the step starts in is the only one the comparison means anything in.
    // A step is at most one voxel of rise, so the two ends differ by a slab only right at a boundary, and
    // treating that as "cannot vouch for it" is the same trade `MoveActiveEntityHandler` already makes.
    val target = ChunkCoords.localise(config, to.x, to.y, config.voxelBaseOf(origin.chunk).toLong())
      ?: return false
    val targetChunk = chunkService.normalise(target.chunk)

    if (!derived.isTracked(targetChunk)) return false

    return derived.canStep(
      from = origin.chunk,
      fromLocalX = origin.localX,
      fromLocalY = origin.localY,
      fromSurface = origin.surface,
      to = targetChunk,
      toLocalX = target.localX,
      toLocalY = target.localY
    )
  }

  override fun surfaceAt(position: Vec3L): Long? {
    if (!chunkService.isReady) return null

    val footing = footingAt(position) ?: return null
    val config = chunkService.config

    // The slab's own base added back: a fractional height inside slab 5 is 1 280 m higher than the same
    // number inside slab 0, and only one of those is an elevation. `standingZ` then rounds it.
    val elevation = (config.voxelBaseOf(footing.chunk) + footing.surface) * config.voxelSize

    return ChunkCoords.standingZ(config, elevation)
  }

  override fun isResident(position: Vec3L): Boolean {
    if (!chunkService.isReady) return false

    val config = chunkService.config
    val localised = ChunkCoords.localise(config, position.x, position.y, voxelZOf(position)) ?: return false

    return chunkService.derived().isTracked(chunkService.normalise(localised.chunk))
  }

  /**
   * The standable surface under [position], searching the slab it is in and then the one below.
   *
   * The slab below is not a nicety. A shaft floor a voxel under a slab boundary belongs to the slab beneath
   * whoever is standing on it, and a column whose ground is a metre down would otherwise report nothing at
   * all - which is the difference between a player descending into the hole they dug and hovering over it.
   *
   * One level only, for the reason [ChunkCoords.offeredSlabs] stops at one: a slab reached this way is a
   * source of ground, not a place to carry on looking down from. It also means the answer can be a long way
   * below the caller when a column genuinely has no closer surface, and that is still the right answer -
   * reporting where the ground is beats reporting that there is none.
   */
  private fun footingAt(position: Vec3L): Footing? {
    val config = chunkService.config
    val derived = chunkService.derived()

    val voxelZ = voxelZOf(position)
    val own = ChunkCoords.localise(config, position.x, position.y, voxelZ) ?: return null

    for (slab in intArrayOf(own.chunk.z, own.chunk.z - 1)) {
      // Only the address is folded across the world seam, never the offset within it: a wrapped axis is a
      // whole number of chunks wide, so the local coordinates are the same either way round.
      val chunk = chunkService.normalise(ChunkPos(own.chunk.x, own.chunk.y, slab))
      if (!derived.isTracked(chunk)) continue

      val walkable = derived.walkableOf(chunk)
      if (walkable.spanCountAt(own.localX, own.localY) == 0) continue

      // Into the tile's own frame before comparing against what it holds. For the slab below this is
      // `localZ + chunkHeight`, so the nearest surface there is the highest one in the column - which is
      // exactly the ground being stood on from above.
      val localZ = (voxelZ - config.voxelBaseOf(chunk)).toDouble()
      val surface = nearestSurface(walkable, own.localX, own.localY, localZ) ?: continue

      return Footing(chunk, own.localX, own.localY, surface)
    }

    return null
  }

  /** Global voxel z of a position. Named rather than inlined for the reason [ChunkCoords] gives. */
  private fun voxelZOf(position: Vec3L) = position.z * ChunkCoords.VOXELS_PER_POSITION_UNIT

  /**
   * The standable surface in a column closest to where the entity thinks it is.
   *
   * A column can have several - one per floor of a building, one per chamber of a cave - and the one that
   * matters is the one under the entity's feet. Picking the lowest would walk anything on an upper floor
   * through it.
   *
   * @param localZ the entity's height in [walkable]'s own slab-local frame, not its global voxel z
   */
  private fun nearestSurface(
    walkable: WalkableTile,
    localX: Int,
    localY: Int,
    localZ: Double
  ): Double? {
    val surfaces = walkable.surfacesAt(localX, localY)
    if (surfaces.isEmpty()) return null

    var best = surfaces[0]
    var bestGap = Math.abs(best - localZ)
    for (i in 1 until surfaces.size) {
      val gap = Math.abs(surfaces[i] - localZ)
      if (gap < bestGap) {
        bestGap = gap
        best = surfaces[i]
      }
    }

    return best
  }
}
