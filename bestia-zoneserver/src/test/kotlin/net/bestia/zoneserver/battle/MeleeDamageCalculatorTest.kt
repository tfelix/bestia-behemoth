package net.bestia.zoneserver.battle

import net.bestia.model.battle.AttackImpl
import net.bestia.model.battle.AttackTarget
import net.bestia.model.battle.AttackType
import net.bestia.model.battle.Element
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.bestia.ConditionValues
import net.bestia.model.entity.BasicStatusBasedValues
import net.bestia.model.entity.StatusBasedValues
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.LevelComponent
import org.junit.jupiter.api.Test

internal class MeleeDamageCalculatorTest {

  private val sut = MeleeDamageCalculator()

  @Test
  fun calculateDamage() {
    val defenderStatusValues = BasicStatusValues(
        strength = 10,
        agility = 10,
        dexterity = 10,
        intelligence = 10,
        magicDefense = 5,
        physicalDefense = 5,
        vitality = 10,
        willpower = 10
    )

    val defenderCondition = ConditionValues(
        currentHealth = 100,
        currentMana = 100,
        currentStamina = 100,
        maxHealth = 100,
        maxMana = 100,
        maxStamina = 100
    )

    val defenderLevel = 5

    val defender = Entity(id = 1)
    defender.addComponent(LevelComponent(entityId = 1, level = defenderLevel, exp = 0))

    val attackerStatusValues = BasicStatusValues(
        strength = 99,
        agility = 10,
        dexterity = 10,
        intelligence = 10,
        magicDefense = 0,
        physicalDefense = 0,
        vitality = 10,
        willpower = 10
    )

    val attackerCondition = ConditionValues(
        currentHealth = 100,
        currentMana = 100,
        currentStamina = 100,
        maxHealth = 100,
        maxMana = 100,
        maxStamina = 100
    )

    val attackerLevel = 5

    val attacker = Entity(id = 1)
    attacker.addComponent(LevelComponent(entityId = 1, level = attackerLevel, exp = 0))

    val ctx = BattleContext(
        attackElement = Element.FIRE,
        defenderElement = Element.FIRE,
        weaponAtk = 10f,
        damageVariables = DamageVariables(),
        usedAttack = AttackImpl(
            databaseName = "test",
            casttime = 100,
            cooldown = 100,
            element = Element.NORMAL,
            hasScript = false,
            manaCost = 10,
            needsLineOfSight = false,
            range = 10,
            strength = 10,
            target = AttackTarget.ENEMY_ENTITY,
            type = AttackType.MELEE_PHYSICAL
        ),
        attackerCondition = attackerCondition,
        defenderCondition = defenderCondition,
        attackerStatusBased = BasicStatusBasedValues(
            statusValues = defenderStatusValues,
            conditionValues = attackerCondition,
            attackSpeed = 10f,
            baseWalkspeed = 10f,
            casttimeMod = 1.0f,
            cooldownMod = 1.0f,
            criticalHitrate = 20,
            dodge = 10,
            hitrate = 10,
            level = attackerLevel,
            minDamage = 60,
            rangedBonusDamage = 0,
            spellDurationMod = 1.0f
        ),
        defenderStatusBased = BasicStatusBasedValues(
            statusValues = defenderStatusValues,
            conditionValues = attackerCondition,
            attackSpeed = 10f,
            baseWalkspeed = 10f,
            casttimeMod = 1.0f,
            cooldownMod = 1.0f,
            criticalHitrate = 20,
            dodge = 10,
            hitrate = 10,
            level = defenderLevel,
            minDamage = 60,
            rangedBonusDamage = 0,
            spellDurationMod = 1.0f
        ),
        attackerStatusPoints = attackerStatusValues,
        defenderStatusPoints = defenderStatusValues,
        attacker = attacker,
        defender = defender
    )
    val dmg = sut.calculateDamage(ctx)
  }
}