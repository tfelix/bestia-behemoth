package net.bestia.zone.stronghold

import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import org.springframework.stereotype.Component

/** Where on the ground a seed's sprout or Empyrite stands. */
@Component
class SeedSpots(
  private val worldService: WorldService,
) {

  /**
   * A first guess from the heightfield; `ChunkStreamSystem.groundNewcomers` stands it on the real column. The
   * offsets in metres place something beside the seed, such as its steward.
   */
  fun standingAt(seed: EmpyriteSeed, offsetXMetres: Double = 0.0, offsetYMetres: Double = 0.0): Vec3L {
    val generated = worldService.generated
    val voxel = generated.config.voxelSize
    val x = seed.x + offsetXMetres
    val y = seed.y + offsetYMetres
    val ground = generated.base.heightAt(x, y)

    return Vec3L((x / voxel).toLong(), (y / voxel).toLong(), ChunkCoords.standingZ(generated.config, ground))
  }
}
