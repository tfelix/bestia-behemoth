package net.bestia.zone.guild.skill

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId

/** The six guild skills that raise one status value of the members around the guild leader by their level. */
enum class GuildAuraSkill(val skill: SkillId, val raise: StatusValueRecalcContext.(Int) -> Unit) {
  STRENGTH(SkillId.GREAT_LEADERSHIP, { strength += it }),
  DEXTERITY(SkillId.SHARP_GAZE, { dexterity += it }),
  VITALITY(SkillId.GLORIOUS_WOUNDS, { vitality += it }),
  AGILITY(SkillId.COLD_HEART, { agility += it }),
  INTELLIGENCE(SkillId.ARCANE_ACCORD, { intelligence += it }),
  WILLPOWER(SkillId.STEADFAST_MIND, { willpower += it });

  companion object {
    /** Only the skills the guild has a level in. */
    fun levelsOf(skillLevel: (String) -> Int): Map<GuildAuraSkill, Int> {
      return entries.associateWith { skillLevel(it.skill.name) }.filterValues { it > 0 }
    }
  }
}
