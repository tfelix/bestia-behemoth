package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 263 (`DESTROYER`): the freed world boss the doomsday marches on the towns. Its level counts
 * the towns it razed, and each one makes it harder to bring down. No trap holds it. Its health grows outside this
 * script, because a mob's maximum health does not come from status values.
 */
@Component
class Destroyer : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return Double.POSITIVE_INFINITY
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    val razed = level - 1
    context.addPercent(
      attack = ATTACK_PERCENT + ATTACK_PERCENT_PER_TOWN * razed,
      defense = DEFENSE_PERCENT + DEFENSE_PERCENT_PER_TOWN * razed,
    )
    context.addHpRegen(percent = REGEN_PERCENT + REGEN_PERCENT_PER_TOWN * razed)
    context.regenerateInCombat()
  }

  private companion object {
    const val ATTACK_PERCENT = 100
    const val ATTACK_PERCENT_PER_TOWN = 25
    const val DEFENSE_PERCENT = 100
    const val DEFENSE_PERCENT_PER_TOWN = 25
    const val REGEN_PERCENT = 200
    const val REGEN_PERCENT_PER_TOWN = 50
  }
}
