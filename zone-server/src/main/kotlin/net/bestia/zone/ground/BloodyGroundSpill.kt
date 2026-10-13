package net.bestia.zone.ground

import net.bestia.zone.battle.ecs.damage.GroundSpill
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Service

/**
 * Turns a death into a stain on the ground.
 *
 * `MarkingGroundTrample`'s sibling, and the thin half of the same pattern: the store decides the shape of the
 * pool and this queues the columns it reached for a resync. The client lays the pool itself on seeing the death.
 */
@Service
class BloodyGroundSpill(
  private val registry: GroundBloodRegistry,
  private val overlay: GroundOverlayService,
  private val clock: BestiaClock,
) : GroundSpill {

  override fun bledAt(voxelX: Long, voxelY: Long) {
    val nowSecond = clock.now().absoluteSecond

    registry.spill(voxelX, voxelY, nowSecond)
      .forEach { overlay.markLevelsDirty(it, GroundLayer.BLOODIED, nowSecond) }
  }
}
