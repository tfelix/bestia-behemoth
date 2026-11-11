package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 215 (`GLORIA`): for 90 s, every item the bearer starts crafting rolls each random stat
 * and socket at 15 points more chance. `CraftingService` asks [rollChanceBonusOf].
 */
@Component
class GloriaGrace : StatusEffectScript {

  override fun durationSeconds(level: Int): Double = DURATION_SECONDS

  companion object {
    fun rollChanceBonusOf(world: World, entityId: EntityId): Int {
      val blessed = world.get(entityId, StatusEffects::class)?.hasEffect(StatusEffectId.GLORIA.id) == true

      return if (blessed) ROLL_CHANCE_BONUS else 0
    }

    private const val DURATION_SECONDS = 90.0
    private const val ROLL_CHANCE_BONUS = 15
  }
}
