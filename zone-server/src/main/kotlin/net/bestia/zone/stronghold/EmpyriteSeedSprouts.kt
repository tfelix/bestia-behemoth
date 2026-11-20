package net.bestia.zone.stronghold

import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.stronghold.ecs.EmpyriteSeedSprout
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import org.springframework.stereotype.Service

/**
 * The sprouts of the awake seeds, as entities. Not stored: every boot raises them again from the awake seeds.
 * Tick thread only after boot.
 */
@Service
class EmpyriteSeedSprouts(
  private val worldService: WorldService,
) {

  private val sproutOf = HashMap<Int, EntityId>()

  fun raise(world: World, seed: EmpyriteSeed) {
    if (seed.rank in sproutOf) return

    sproutOf[seed.rank] = world.createEntity { id ->
      add(id, Position.fromVec3(standingAt(seed)))
      add(id, EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.EMPYRITE_SEED.ordinal.toLong()))
      add(id, EmpyriteSeedSprout(seed.rank))
    }
  }

  /** The seed was planted: the site stands where the sprout did. */
  fun uproot(world: World, rank: Int) {
    sproutOf.remove(rank)?.let(world::destroy)
  }

  fun sproutOf(rank: Int): EntityId? {
    return sproutOf[rank]
  }

  /** A first guess from the heightfield; `ChunkStreamSystem.groundNewcomers` stands it on the real column. */
  private fun standingAt(seed: EmpyriteSeed): Vec3L {
    val generated = worldService.generated
    val voxel = generated.config.voxelSize
    val ground = generated.base.heightAt(seed.x, seed.y)

    return Vec3L((seed.x / voxel).toLong(), (seed.y / voxel).toLong(), ChunkCoords.standingZ(generated.config, ground))
  }
}
