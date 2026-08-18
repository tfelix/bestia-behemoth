package net.bestia.zone.battle

import net.bestia.zone.battle.skill.AttackType
import net.bestia.zone.battle.skill.BattleAttack
import net.bestia.zone.battle.damage.DamageVariables
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.battle.status.DefenseValues
import net.bestia.zone.battle.status.DerivedStatusValues
import net.bestia.zone.battle.status.StatusValues

object BattleContextFixture {

  fun entityCtx(
    attack: BattleAttack = attack(),
    attackerEntity: BattleEntity = battleEntity(),
    defenderEntity: BattleEntity = battleEntity()
  ): BattleContext {
    return EntityBattleContext(
      usedAttack = attack,
      attacker = attackerEntity,
      damageVariables = DamageVariables(),
      defender = defenderEntity,
      weapon = Weapon(atk = 10, upgradeLevel = 0, matk = 0)
    )
  }

  fun attack(
    level: Int = 1,
    aoeRadius: Double? = null
  ): BattleAttack {
    return BattleAttack(
      strength = 0,
      manaCost = 10,
      range = 5,
      attackType = AttackType.NO_DAMAGE,
      needsLineOfSight = false,
      aoeRadius = aoeRadius,
      attackElement = Element.NORMAL,
      level = level,
      script = null
    )
  }

  fun groundCtx(
    attack: BattleAttack = attack(),
    attackerEntity: BattleEntity = battleEntity(),
    targetPosition: Vec3L = Vec3L(3, 0, 0)
  ): BattleContext {
    return GroundBattleContext(
      usedAttack = attack,
      attacker = attackerEntity,
      damageVariables = DamageVariables(),
      weapon = Weapon(atk = 10, upgradeLevel = 0, matk = 0),
      targetPosition = targetPosition
    )
  }

  fun battleEntity(
    level: Int = 10,
    intelligence: Int = 10,
    maxHealth: Int = 0,
    activeEffectIds: Set<Long> = emptySet()
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
      assumedElement = Element.NORMAL,
      maxHealth = maxHealth,
      activeEffectIds = activeEffectIds
    )
  }
}
