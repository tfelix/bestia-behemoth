package net.bestia.zone.world

import net.bestia.worldgen.voxel.BlockType
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.persistence.MasterSpawnPoint
import net.bestia.zone.world.settlement.SettlementFates
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Service
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Which homes a new master is offered now: the first [MasterSpawnPointService.HOMES_OFFERED] spawn points, by
 * rank, whose town still stands and whose ground is dry. With none left, one start anyway, on dry land near the
 * first home.
 *
 * Decided on the tick, where the voxels are, and read from IO threads as a snapshot.
 */
@Service
class SpawnPointAvailability(
  private val chunkService: ChunkService,
  private val fates: SettlementFates,
) : SafeGround {

  /** One home on offer. [id] is the spawn point row a client names when it picks this home. */
  data class Offer(val id: Long, val settlementName: String, val tier: String, val position: Vec3L)

  @Volatile
  private var points: List<MasterSpawnPoint> = emptyList()

  @Volatile
  private var offers: List<Offer> = emptyList()

  fun offered(): List<Offer> {
    return offers
  }

  override fun dryHome(): Vec3L? {
    return offers.firstOrNull()?.position
  }

  /** The name masters know [settlement] by as a home, or null for a settlement that never was one. */
  fun homeNameOf(settlement: Int): String? {
    return points.firstOrNull { it.settlementIndex == settlement }?.settlementName
  }

  /** The spawn points in rank order. At boot, before the tick decides which are usable. */
  fun load(points: List<MasterSpawnPoint>) {
    this.points = points
    offers = points.take(MasterSpawnPointService.HOMES_OFFERED).map(::offerOf)
  }

  /** Decides the offers again. On the tick. */
  fun refresh() {
    val usable = points
      .filter { !fates.hasFallen(it.settlementIndex) && isDry(it.position.x, it.position.y) }
      .take(MasterSpawnPointService.HOMES_OFFERED)

    offers = if (usable.isNotEmpty()) usable.map(::offerOf) else listOfNotNull(fallback())
  }

  /** The first home, moved to the nearest dry land. Null only when nothing within reach is dry. */
  private fun fallback(): Offer? {
    val first = points.firstOrNull() ?: return null
    val dry = dryNear(first.position) ?: return null

    return offerOf(first).copy(position = dry)
  }

  private fun dryNear(centre: Vec3L): Vec3L? {
    for (radius in 0..FALLBACK_REACH step FALLBACK_STEP) {
      val samples = if (radius == 0) 1 else FALLBACK_BEARINGS
      for (bearing in 0 until samples) {
        val angle = 2 * Math.PI * bearing / samples
        val x = centre.x + (radius * cos(angle)).roundToLong()
        val y = centre.y + (radius * sin(angle)).roundToLong()

        if (isDry(x, y)) return Vec3L(x, y, groundZ(x, y)!!)
      }
    }
    return null
  }

  /** Judged at the generated ground height, so a stored position whose height went stale still reads right. */
  override fun isDry(x: Long, y: Long): Boolean {
    val ground = groundZ(x, y) ?: return false
    val config = chunkService.config
    if (ground < config.voxelZOf(config.seaLevel)) return false

    return (ground..ground + 2).none { z -> blockAt(x, y, z) == WATER_ID }
  }

  private fun groundZ(x: Long, y: Long): Long? {
    val elevation = chunkService.surfaceElevationAt(x, y) ?: return null
    return chunkService.config.voxelZOf(elevation).toLong()
  }

  private fun blockAt(x: Long, y: Long, z: Long): Byte? {
    val config = chunkService.config
    val localised = ChunkCoords.localise(config, x, y, z) ?: return null
    val index = ChunkCoords.voxelIndex(config, localised.localX, localised.localY, localised.localZ)

    return chunkService.merged(chunkService.normalise(localised.chunk)).blocks[index]
  }

  private fun offerOf(point: MasterSpawnPoint): Offer {
    return Offer(point.id, point.settlementName, point.tier, point.position)
  }

  private companion object {
    private val WATER_ID = BlockType.WATER.id.toByte()

    /** How far from the first home the fallback looks for dry land, in voxels. */
    const val FALLBACK_REACH = 256
    const val FALLBACK_STEP = 16
    const val FALLBACK_BEARINGS = 16
  }
}
