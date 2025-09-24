package net.bestia.zone.battle

import net.bestia.zone.battle.attack.AttackType
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.battle.damage.DamageVariables
import net.bestia.zone.geometry.Vec3
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.status.DefenseValues
import net.bestia.zone.status.DerivedStatusValues
import net.bestia.zone.status.StatusValues

object BattleContextFixture {

  fun entityCtx(
    attack: BattleAttack = attack(),
    attackerEntity: BattleEntity = battleEntity()
  ): BattleContext {
    return EntityBattleContext(
      usedAttack = attack,
      attacker = attackerEntity,
      damageVariables = DamageVariables(),
      defender = battleEntity(),
      weapon = Weapon(atk = 10, upgradeLevel = 0, matk = 0)
    )
  }

  fun attack(
    level: Int = 1
  ): BattleAttack {
    return BattleAttack(
      strength = 0,
      manaCost = 10,
      range = 5,
      attackType = AttackType.NO_DAMAGE,
      needsLineOfSight = false,
      attackElement = Element.NORMAL,
      level = level,
      script = null
    )
  }

  fun battleEntity(
    level: Int = 10,
    intelligence: Int = 10
  ): BattleEntity {
    val statusValues = StatusValues(
      agility = 10,
      strength = 10,
      dexterity = 10,
      intelligence = intelligence,
      vitality = 10,
      willpower = 10
    )

    return BattleEntity(
      id = 1,
      position = Vec3L(1, 0, 0),
      level = level,
      defense = DefenseValues(
        defense = 10,
        magicDefense = 20
      ),
      statusValues = statusValues,
      derivedStatusValues = DerivedStatusValues.fromStatusValues(level, statusValues),
      assumedElement = Element.NORMAL
    )
  }
}