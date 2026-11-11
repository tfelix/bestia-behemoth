package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 214 (`MAGNIFICAT`): `+10%/lv` HP and mana regeneration for 90 s. */
@Component
class MagnificatHymn : StatusEffectScript {

  override fun durationSeconds(level: Int): Double = DURATION_SECONDS

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    val percent = REGEN_PERCENT_PER_LEVEL * effect.level.coerceIn(1, MAX_LEVEL)

    context.addHpRegen(percent = percent)
    context.addManaRegen(percent = percent)
  }

  private companion object {
    const val DURATION_SECONDS = 90.0
    const val REGEN_PERCENT_PER_LEVEL = 10
    const val MAX_LEVEL = 3
  }
}
