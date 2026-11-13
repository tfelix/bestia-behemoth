package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 234 (`BATTLE_HYMN`): a Bard's Battle Hymn, `+3%/lv` ATK and MATK. */
@Component
class BattleHymnHeard : HeardSongScript() {

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    // The attack percent raises MATK as well.
    context.addPercent(attack = Songcraft.boosted(ATTACK_PERCENT_PER_LEVEL * songLevel(effect.level), effect.amount))
  }

  private companion object {
    const val ATTACK_PERCENT_PER_LEVEL = 3
  }
}
