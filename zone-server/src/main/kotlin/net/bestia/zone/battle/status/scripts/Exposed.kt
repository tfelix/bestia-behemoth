package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** What Strip Armor leaves on a wild bestia, which wears no armour to take: a quarter less defence. */
@Component
class Exposed : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 15.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.addPercent(defense = DEFENSE_PERCENT)
  }

  private companion object {
    const val DEFENSE_PERCENT = -25
  }
}
