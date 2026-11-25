package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 257 (`MANA_SICKNESS`): a master standing in mana at the Sickness tier. A fifth less
 * health, a tenth less attack, and no natural healing. Exposure renews it while the master stays; it fades a
 * minute after they leave.
 */
@Component
class ManaSickness : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return LINGER_SECONDS
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.addMaxPools(hpPercent = -20)
    context.addPercent(attack = -10)
    context.addHpRegen(percent = -100)
  }

  private companion object {
    const val LINGER_SECONDS = 60.0
  }
}
