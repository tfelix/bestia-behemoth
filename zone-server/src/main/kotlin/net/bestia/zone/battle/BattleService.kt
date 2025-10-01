package net.bestia.zone.battle

import net.bestia.zone.battle.attack.AttackRepository
import net.bestia.zone.battle.attack.findByIdOrThrow
import net.bestia.zone.util.EntityId


class BattleService(
  private val attackRepository: AttackRepository,
) {

  fun attackEntity(
    attacker: EntityId,
    target: EntityId,
    usedAttackId: Long,
  ) {
    val usedAttack = attackRepository.findByIdOrThrow(usedAttackId)

    // Which element is used by the attack? is it a spell (no direct element)

    // are the resources there for the attack?

    // build entity battle ctx

    // build attack strategy
  }
}