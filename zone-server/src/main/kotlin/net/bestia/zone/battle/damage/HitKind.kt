package net.bestia.zone.battle.damage

import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.SkillArchetype
import net.bestia.zone.battle.Element

/**
 * What sort of blow a hit is. It decides which of the damage modifiers in [DamageModifiers] weigh it, and lets a
 * status that reacts to a hit tell a sword from a spell.
 */
data class HitKind(
  val form: HitForm,
  val reach: HitReach,
  val element: Element,
) {

  companion object {
    /** A burn, a poison tick, a share passed on by a ward: no modifier table weighs it. */
    val UNTYPED = HitKind(HitForm.OTHER, HitReach.MELEE, Element.NORMAL)

    fun physical(reach: HitReach, element: Element = Element.NORMAL) = HitKind(HitForm.PHYSICAL, reach, element)

    fun magic(element: Element = Element.NORMAL) = HitKind(HitForm.MAGIC, HitReach.RANGED, element)

    /**
     * A skill's blows by its archetype: a strike is a melee weapon blow, a shot a ranged one, everything else a
     * spell. A master skill has no archetype and counts as a spell unless its script says otherwise.
     */
    fun ofSkill(skill: Skill): HitKind {
      val element = Element.valueOf(skill.element)

      return when (skill.archetype) {
        SkillArchetype.STRIKE, SkillArchetype.POWER_STRIKE, SkillArchetype.ULTIMATE -> physical(HitReach.MELEE, element)
        SkillArchetype.SHOT -> physical(HitReach.RANGED, element)
        else -> magic(element)
      }
    }
  }
}

enum class HitForm {
  PHYSICAL,
  MAGIC,

  /** Neither a weapon nor a spell: damage over time, a reflected or passed-on share. */
  OTHER,
}

enum class HitReach {
  MELEE,
  RANGED,
}
