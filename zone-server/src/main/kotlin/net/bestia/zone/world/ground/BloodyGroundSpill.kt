package net.bestia.zone.world.ground

import net.bestia.zone.ecs.battle.damage.GroundSpill
import net.bestia.zone.environment.time.BestiaClock
import org.springframework.stereotype.Service

/**
 * Turns a death into a stain on the ground.
 *
 * `MarkingGroundTrample`'s sibling, and the thin half of the same pattern: the store decides the shape of the
 * pool and this decides who is told about it.
 */
@Service
class BloodyGroundSpill(
  private val registry: GroundBloodRegistry,
  private val overlay: GroundOverlayService,
  private val clock: BestiaClock,
) : GroundSpill {

  override fun bledAt(voxelX: Long, voxelY: Long) {
    registry.spill(voxelX, voxelY, clock.now().absoluteSecond)
      .forEach { overlay.markLayersDirty(it) }
  }
}
