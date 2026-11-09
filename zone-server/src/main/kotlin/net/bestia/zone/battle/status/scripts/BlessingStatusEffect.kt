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
 * Ragnarok Online's Blessing: +level STR, DEX and INT and +2 HIT per level, for 40 s plus 20 s per level. On an
 * Undead-element or Demon-kind target it turns on them instead - their DEX and INT are halved and their STR is left
 * alone.
 */
@Component
class BlessingStatusEffect : StatusEffectScript {

  override fun durationSeconds(level: Int): Double = 40.0 + 20.0 * level

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    if (isCursedBy(world.get(entityId, Nature::class))) {
      context.dexterity /= 2
      context.intelligence /= 2
      return
    }

    context.strength += level
    context.dexterity += level
    context.intelligence += level
    context.addAccuracy(hit = HIT_PER_LEVEL * level)
  }

  /** An entity with no Nature - a master, a prop - counts as Normal, as everywhere else. */
  private fun isCursedBy(nature: Nature?): Boolean =
    nature != null && (nature.element in UNDEAD_ELEMENTS || nature.kind == BestiaKind.DEMON)

  private companion object {
    const val HIT_PER_LEVEL = 2

    val UNDEAD_ELEMENTS = setOf(Element.UNDEAD, Element.UNDEAD_2, Element.UNDEAD_3, Element.UNDEAD_4)
  }
}
