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

  /** A first guess from the heightfield; `ChunkStreamSystem.groundNewcomers` stands it on the real column. */
  fun standingAt(seed: EmpyriteSeed): Vec3L {
    val generated = worldService.generated
    val voxel = generated.config.voxelSize
    val ground = generated.base.heightAt(seed.x, seed.y)

    return Vec3L((seed.x / voxel).toLong(), (seed.y / voxel).toLong(), ChunkCoords.standingZ(generated.config, ground))
  }
}
