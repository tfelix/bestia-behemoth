package net.bestia.zone.scenarios

import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.mining.OreYield
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Component
import kotlin.math.hypot
import kotlin.math.min

/**
 * Real ore in the shared test world, found the way a prospector would: at a deposit, then block by block. The
 * mining scenarios need ore they did not place, because nothing can place a voxel.
 */
@Component
class OreSpots(
  private val worldService: WorldService,
  private val chunks: ChunkService,
  private val oreYield: OreYield,
  private val world: WorldView,
) {

  /** The ore voxel nearest [near] whose item matches [wanted]. Reads chunks inside the lease, like the tick. */
  fun find(near: Vec3L, wanted: (OreYield.Stack) -> Boolean): Vec3L {
    val deposits = worldService.generated.world.features.query(Aabb(-SEARCH, -SEARCH, SEARCH, SEARCH))
      .filter { it.kind == FeatureKind.ORE_DEPOSIT }
      .filterIsInstance<PointMarker>()
      .sortedBy { hypot(it.position.x - near.x, it.position.y - near.y) }

    return world.read {
      deposits.firstNotNullOfOrNull { deposit -> oreAt(deposit, wanted) }
    } ?: error("No ore in the test world matches")
  }

  /** The item the ore at [voxel] comes out as. */
  fun itemAt(voxel: Vec3L): Long = world.read { stackAt(voxel) }?.itemId ?: error("No ore at $voxel")

  private fun stackAt(voxel: Vec3L): OreYield.Stack? {
    val local = ChunkCoords.localise(chunks.config, voxel.x, voxel.y, voxel.z) ?: return null
    val block = chunks.merged(chunks.normalise(local.chunk))[local.localX, local.localY, local.localZ]

    return block?.let(oreYield::of)
  }

  private fun oreAt(deposit: PointMarker, wanted: (OreYield.Stack) -> Boolean): Vec3L? {
    val x = deposit.position.x.toLong()
    val y = deposit.position.y.toLong()
    val surface = chunks.surfaceElevationAt(x, y)?.toLong() ?: return null
    val centreZ = surface - deposit.attribute(DepositChannels.DEPTH).toLong()
    val reach = min(deposit.attribute(DepositChannels.RADIUS).toLong(), MAX_REACH)

    for (dz in -reach..reach) for (dx in -reach..reach) for (dy in -reach..reach) {
      val voxel = Vec3L(x + dx, y + dy, centreZ + dz)
      val stack = stackAt(voxel) ?: continue
      if (wanted(stack)) return voxel
    }

    return null
  }

  private companion object {
    const val SEARCH = 1_000_000.0
    const val MAX_REACH = 12L
  }
}
