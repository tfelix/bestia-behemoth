package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 222 (`CLOAKING`), the Assassin's Cloak: unseen while walking, slower, until the bearer
 * attacks or casts. Unlike a bestia's [Cloaked], a blow does not give it away.
 */
@Component
class Cloaking(visibility: EntityVisibility) : Concealment(visibility) {

  override val revealedBy: Set<EntityAction> = setOf(EntityAction.ATTACKED, EntityAction.CAST)

  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return Double.POSITIVE_INFINITY
  }

  override fun upkeep(effect: ActiveStatusEffect, mana: Mana): Int {
    return drainPerSecond(effect.level)
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.speed *= speedPercent(effect.level) / 100f
  }

  companion object {
    private const val MAX_LEVEL = 3

    /** Cloak's table: 50% of the speed at Lv.1, 20% more each level. */
    fun speedPercent(level: Int): Int {
      return 50 + 20 * (level.coerceIn(1, MAX_LEVEL) - 1)
    }

    /** Cloak's table: 6 mana a second at Lv.1, one less each level. */
    fun drainPerSecond(level: Int): Int {
      return 7 - level.coerceIn(1, MAX_LEVEL)
    }
  }
}
