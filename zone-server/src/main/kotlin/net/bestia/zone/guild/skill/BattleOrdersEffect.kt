package net.bestia.zone.guild.skill

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 13 (`BATTLE_ORDERS`), from [BattleOrders]. */
@Component
class BattleOrdersEffect : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 180.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.strength += BONUS
    context.dexterity += BONUS
    context.intelligence += BONUS
  }

  private companion object {
    const val BONUS = 5
  }
}
