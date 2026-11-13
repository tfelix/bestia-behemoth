package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 236 (`WAR_MARCH`): a Bard's War March, `+2%/lv` move and attack speed. */
@Component
class WarMarchHeard : HeardSongScript() {

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    val percent = Songcraft.boosted(SPEED_PERCENT_PER_LEVEL * songLevel(effect.level), effect.amount)

    context.speed *= 1f + percent / 100f
    context.addPercent(aspd = percent)
  }

  private companion object {
    const val SPEED_PERCENT_PER_LEVEL = 2
  }
}
