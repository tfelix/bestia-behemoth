package net.bestia.zone.prop

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.ceil
import kotlin.math.hypot

/** The player structures around a spot, finished or not, read from the registry's chunk columns. Tick thread only. */
@Component
class StructureNeighbourhood(
  private val structures: PlayerStructureRegistry,
  private val worldService: WorldService,
) {

  fun within(at: Vec3L, rangeMetres: Double): List<StructureEntry> {
    val config = worldService.config
    val chunkSize = config.chunkSize.toLong()
    val rangeUnits = ceil(rangeMetres / config.voxelSize).toLong()
    val firstChunkX = Math.floorDiv(at.x - rangeUnits, chunkSize).toInt()
    val lastChunkX = Math.floorDiv(at.x + rangeUnits, chunkSize).toInt()
    val firstChunkY = Math.floorDiv(at.y - rangeUnits, chunkSize).toInt()
    val lastChunkY = Math.floorDiv(at.y + rangeUnits, chunkSize).toInt()

    return (firstChunkX..lastChunkX)
      .flatMap { chunkX -> (firstChunkY..lastChunkY).flatMap { chunkY -> structures.`in`(chunkX, chunkY) } }
      .filter { metresBetween(it.position, at) <= rangeMetres }
  }

  /** Across the ground only, like a settlement's sphere: a storey up is no further away. */
  fun metresBetween(a: Vec3L, b: Vec3L): Double {
    return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) * worldService.config.voxelSize
  }
}
