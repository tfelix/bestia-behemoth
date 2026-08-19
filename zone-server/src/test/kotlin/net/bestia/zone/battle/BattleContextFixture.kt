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
    needsLineOfSight: Boolean = false,
    element: Element = Element.NORMAL
  ): BattleAttack {
    return BattleAttack(
      strength = 0,
      manaCost = 10,
      range = range,
      attackType = AttackType.MELEE_PHYSICAL,
      needsLineOfSight = needsLineOfSight,
      aoeRadius = aoeRadius,
      attackElement = element,
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

  /**
   * [defense] and [magicDefense] default to null, meaning "whatever [DefenseValues.fromStatusValues] derives
   * from these attributes" - which is what a real entity has. Pass them only to pin a defence independently of
   * the attributes, which the damage tests need in order to move one term at a time.
   */
  fun battleEntity(
    level: Int = 10,
    intelligence: Int = 10,
    strength: Int = 10,
    dexterity: Int = 10,
    agility: Int = 10,
    vitality: Int = 10,
    willpower: Int = 10,
    defense: Int? = null,
    magicDefense: Int? = null,
    element: Element = Element.NORMAL,
    maxHealth: Int = 0,
    activeEffectIds: Set<Long> = emptySet(),
    id: Long = ATTACKER_ID
  ): BattleEntity {
    val statusValues = StatusValues(
      agility = agility,
      strength = strength,
      dexterity = dexterity,
      intelligence = intelligence,
      vitality = vitality,
      willpower = willpower
    )

    val derivedDefense = DefenseValues.fromStatusValues(level, statusValues)

    return BattleEntity(
      id = id,
      position = Vec3L(1, 0, 0),
      level = level,
      defense = DefenseValues(
        defense = defense ?: derivedDefense.defense,
        magicDefense = magicDefense ?: derivedDefense.magicDefense
      ),
      statusValues = statusValues,
      derivedStatusValues = DerivedStatusValues.fromStatusValues(level, statusValues),
      assumedElement = element,
      maxHealth = maxHealth,
      activeEffectIds = activeEffectIds
    )
  }
}
