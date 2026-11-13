package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 238 (`MIRAGE_DANCE`): a Bard's Mirage Dance, `+3/lv` FLEE. */
@Component
class MirageDanceHeard : HeardSongScript() {

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.addAccuracy(flee = Songcraft.boosted(FLEE_PER_LEVEL * songLevel(effect.level), effect.amount))
  }

  private companion object {
    const val FLEE_PER_LEVEL = 3
  }
}
