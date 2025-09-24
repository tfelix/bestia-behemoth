package net.bestia.zone.battle

import net.bestia.zone.battle.attack.AttackRepository
import net.bestia.zone.battle.attack.findByIdOrThrow
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.EntityRegistry


class BattleService(
  private val attackRepository: AttackRepository,
  private val entityRegistry: EntityRegistry
) {

  fun attackEntity(
    attacker: EntityId,
    target: EntityId,
    usedAttackId: Long,
  ) {
    val usedAttack = attackRepository.findByIdOrThrow(usedAttackId)

    val attackerEntity = entityRegistry.getEntity(attacker)
    val targetEntity = entityRegistry.getEntity(target)

    // Which element is used by the attack? is it a spell (no direct element)

    // are the resources there for the attack?

    // build entity battle ctx

    // build attack strategy
  }
}