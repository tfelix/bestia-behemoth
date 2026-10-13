package net.bestia.zone.battle.status.scripts

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
 * Ragnarok Online's Blessing: +level STR, DEX and INT for 40 s plus 20 s per level, the numbers the table in
 * `skills.yml` gives. On an Undead-element target it turns on them instead - their DEX and INT are halved and
 * their STR is left alone.
 *
 * Two things the description names are not modeled. Its "HIT Increase" column has no channel: HIT is derived
 * from effective DEX (`DerivedStatusValues`), so the +level DEX already raises it by that much, and the flat
 * extra would need a new term carried from the recalc context to the hit roll. And "Demon race" has nothing to
 * test, since no creature carries a race.
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
    // An entity with no Nature - a master, a prop - counts as Normal, as everywhere else.
    val element = world.get(entityId, Nature::class)?.element

    if (element in UNDEAD_ELEMENTS) {
      context.dexterity /= 2
      context.intelligence /= 2
      return
    }

    context.strength += level
    context.dexterity += level
    context.intelligence += level
  }

  private companion object {
    val UNDEAD_ELEMENTS = setOf(Element.UNDEAD, Element.UNDEAD_2, Element.UNDEAD_3, Element.UNDEAD_4)
  }
}
