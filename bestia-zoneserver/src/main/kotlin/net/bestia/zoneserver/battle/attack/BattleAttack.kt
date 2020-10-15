package net.bestia.zoneserver.battle.attack

import net.bestia.model.battle.Attack
import net.bestia.model.battle.AttackType

data class BattleAttack(
    val strength: Int,
    val manaCost: Int,
    val range: Long,
    val attackType: AttackType,
    val needsLineOfSight: Boolean
) {
  companion object {
    fun fromAttack(attack: Attack): BattleAttack {
      return BattleAttack(
          strength = attack.strength,
          range = attack.range,
          attackType = attack.type,
          needsLineOfSight = attack.needsLineOfSight,
          manaCost = attack.manaCost
      )
    }
  }
}