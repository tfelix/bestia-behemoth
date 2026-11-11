package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 209 (`SIGNUM_CRUCIS`): the Priest's sigil. The bearer loses `10%/lv` of its physical
 * defence, and a Demon or an Undead `10%/lv` more, for `30 s + 15 s/lv` from level 2.
 */
@Component
class SignumCrucisSigil : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double = BASE_SECONDS + SECONDS_PER_LEVEL * (level - 1)

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.cutDefense(defenseCutPercent(effect.level, world.get(entityId, Nature::class)))
  }

  companion object {
    fun defenseCutPercent(level: Int, nature: Nature?): Int {
      val unholy = nature != null &&
        (nature.element.base == Element.UNDEAD || nature.kind == BestiaKind.UNDEAD || nature.kind == BestiaKind.DEMON)
      val percentPerLevel = if (unholy) PERCENT_PER_LEVEL * 2 else PERCENT_PER_LEVEL

      return percentPerLevel * level.coerceIn(1, MAX_LEVEL)
    }

    private const val BASE_SECONDS = 30.0
    private const val SECONDS_PER_LEVEL = 15.0
    private const val PERCENT_PER_LEVEL = 10
    private const val MAX_LEVEL = 3
  }
}
