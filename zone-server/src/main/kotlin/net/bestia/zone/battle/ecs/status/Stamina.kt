package net.bestia.zone.battle.ecs.status

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.battle.status.CurMax

class Stamina(
  current: Int,
  max: Int
) : CurMax(current, max), Component {

  /**
   * When the weather last bit, in ticks, set by `EnvironmentalExposureSystem`. Nothing comes back by rest while the
   * body is out of its comfort band, see [StaminaRegenSystem]. Never synced: the client reads the weather itself.
   */
  var exposedAtTick: Long = NEVER

  fun isExposed(nowTick: Long, withinTicks: Long): Boolean {
    return exposedAtTick != NEVER && nowTick - exposedAtTick <= withinTicks
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return StaminaComponentSMSG(
      entityId = entityId,
      current = current,
      max = max
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.OwnerOnly

  companion object {
    const val NEVER = Long.MIN_VALUE
  }
}
