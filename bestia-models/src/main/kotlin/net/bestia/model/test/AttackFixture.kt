package net.bestia.model.test

import net.bestia.model.battle.*

object AttackFixture {
  @JvmStatic
  fun createAttack(
      attackDbName: String = "Tackle",
      attackRepository: AttackRepository
  ): Attack {
    return Attack(
        databaseName = attackDbName,
        element = Element.NORMAL,
        type = AttackType.MELEE_PHYSICAL,
        strength = 10,
        target = AttackTarget.ENEMY_ENTITY,
        range = 10,
        needsLineOfSight = false,
        manaCost = 10,
        hasScript = false,
        cooldown = 100
    ).also { attackRepository.save(it) }
  }
}