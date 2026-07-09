package net.bestia.zone.battle

import net.bestia.zone.battle.skill.SkillRepository
import net.bestia.zone.battle.skill.findByIdOrThrow
import net.bestia.zone.util.EntityId


class BattleService(
  private val skillRepository: SkillRepository,
) {

  fun attackEntity(
    attacker: EntityId,
    target: EntityId,
    usedAttackId: Long,
  ) {
    val usedAttack = skillRepository.findByIdOrThrow(usedAttackId)

    // Which element is used by the attack? is it a spell (no direct element)

    // are the resources there for the attack?

    // build entity battle ctx

    // build attack strategy
  }
}