package net.bestia.zone.ground.fire

import net.bestia.zone.battle.GroundIgnition
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** A burning body sets the tile it stands on alight, credited to whoever set it burning. */
@Component
class BurningGroundIgnition(private val groundFire: GroundFireService) : GroundIgnition {

  override fun ignite(at: Vec3L, sourceEntityId: EntityId) {
    groundFire.ignite(at, radiusTiles = 0, casterId = sourceEntityId, skillId = NO_SKILL, skillLevel = 1)
  }

  private companion object {
    const val NO_SKILL = 0L
  }
}
