package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 240 (`WANDERERS_TUNE`): a Bard's Wanderer's Tune, `-10%/lv` stamina drain - the same drain
 * Tough Guy cuts.
 */
@Component
class WanderersTuneHeard : HeardSongScript() {

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.staminaDrainReduction += Songcraft.boosted(DRAIN_PERCENT_PER_LEVEL * songLevel(effect.level), effect.amount)
  }

  private companion object {
    const val DRAIN_PERCENT_PER_LEVEL = 10
  }
}
