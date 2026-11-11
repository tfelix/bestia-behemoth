package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Registered under `status_effects.yml` id 5 (`BLESSING`), applied by the BLESSING skill (see
 * `net.bestia.zone.casting.scripts.Blessing` - a distinct class registered in the skill
 * script registry; named differently here since Spring's default bean name is the decapitalised
 * simple class name regardless of package, and both scripts being called plain `Blessing` collided).
 *
 * Ragnarok Online's Blessing with Impositio Manus folded in, over 5 levels instead of 10, so every level counts
 * twice. On an Undead-element or Demon-kind target it turns on them instead - their DEX and INT are halved and their
 * STR is left alone.
 */
@Component
class BlessingStatusEffect : StatusEffectScript {

  override fun durationSeconds(level: Int): Double = 40.0 + 40.0 * level

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    if (curses(world.get(entityId, Nature::class))) {
      context.dexterity /= 2
      context.intelligence /= 2
      return
    }

    context.strength += STAT_PER_LEVEL * level
    context.dexterity += STAT_PER_LEVEL * level
    context.intelligence += STAT_PER_LEVEL * level
    context.addAccuracy(hit = HIT_PER_LEVEL * level)
    context.addAttack(atk = ATTACK_PER_LEVEL * level, matk = ATTACK_PER_LEVEL * level)
  }

  companion object {
    /** Whether a blessing turns on [nature]. An entity with no Nature - a master, a prop - counts as Normal. */
    fun curses(nature: Nature?): Boolean =
      nature != null && (nature.element.base == Element.UNDEAD || nature.kind == BestiaKind.DEMON)

    private const val STAT_PER_LEVEL = 2
    private const val HIT_PER_LEVEL = 4
    private const val ATTACK_PER_LEVEL = 3
  }
}
