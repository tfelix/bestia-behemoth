package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/** Endure (`skills.yml` id 96): HP and mana keep regenerating in combat. */
@Component
class Endure : PassiveSkillScript {

  override val skill = SkillId.M_ENDURE

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.regenerateInCombat()
  }
}
