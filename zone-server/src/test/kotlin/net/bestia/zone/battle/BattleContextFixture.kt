package net.bestia.zone.battle

import net.bestia.zone.battle.skill.AttackType
import net.bestia.zone.battle.skill.BattleAttack
import net.bestia.zone.battle.damage.DamageVariables
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.battle.status.DefenseValues
import net.bestia.zone.battle.status.DerivedStatusValues
import net.bestia.zone.battle.status.StatusValues

object BattleContextFixture {

  const val ATTACKER_ID = 1L
  const val DEFENDER_ID = 2L

  fun entityCtx(
    attack: BattleAttack = attack(),
    attackerEntity: BattleEntity = battleEntity(),
    // A distinct id, so a test asserting which side something landed on cannot pass by accident.
    defenderEntity: BattleEntity = battleEntity(id = DEFENDER_ID)
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
    aoeRadius: Double? = null,
    range: Long = 5,
    needsLineOfSight: Boolean = false
  ): BattleAttack {
    return BattleAttack(
      strength = 0,
      manaCost = 10,
      range = range,
      attackType = AttackType.MELEE_PHYSICAL,
      needsLineOfSight = needsLineOfSight,
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
    activeEffectIds: Set<Long> = emptySet(),
    id: Long = ATTACKER_ID
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
      id = id,
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
