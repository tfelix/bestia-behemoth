package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 262 (`CORRUPTED`): a wild bestia born on land the Withering rotted. Fiercer than its kin,
 * and no trap holds it. Marks the creature for the client's look too.
 */
@Component
class Corrupted : StatusEffectScript {

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
    context.addPercent(attack = ATTACK_PERCENT)
  }

  private companion object {
    const val ATTACK_PERCENT = 20
  }
}
