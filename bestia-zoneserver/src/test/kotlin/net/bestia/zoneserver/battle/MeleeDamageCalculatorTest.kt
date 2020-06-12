package net.bestia.zoneserver.battle

import net.bestia.model.battle.AttackType
import net.bestia.model.battle.Element
import net.bestia.model.bestia.BasicDefense
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.bestia.ConditionValues
import net.bestia.model.entity.BasicStatusBasedValues
import net.bestia.zoneserver.battle.damage.DamageVariables
import net.bestia.zoneserver.battle.damage.MeleePhysicalDamageCalculator
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.ConditionComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.junit.jupiter.api.Test

internal class MeleeDamageCalculatorTest {

  private val sut = MeleePhysicalDamageCalculator()

  @Test
  fun calculateDamage() {
    val defenderStatusValues = BasicStatusValues(
        strength = 10,
        agility = 10,
        dexterity = 10,
        intelligence = 10,
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

    val defender = Entity(id = 1).apply {
      addComponent(LevelComponent(entityId = 1, level = defenderLevel, exp = 0))
      addComponent(StatusComponent(
          entityId = 1,
          statusValues = defenderStatusValues,
          statusBasedValues = BasicStatusBasedValues(defenderStatusValues, defenderLevel),
          defense = BasicDefense()
      ))
      addComponent(ConditionComponent(
          entityId = 1,
          conditionValues = defenderCondition
      ))
    }

    val attackerStatusValues = BasicStatusValues(
        strength = 99,
        agility = 10,
        dexterity = 10,
        intelligence = 10,
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

    val attacker = Entity(id = 2).apply {
      addComponent(LevelComponent(entityId = 2, level = attackerLevel, exp = 0))
      addComponent(StatusComponent(
          entityId = 2,
          statusValues = attackerStatusValues,
          statusBasedValues = BasicStatusBasedValues(attackerStatusValues, attackerLevel),
          defense = BasicDefense()
      ))
      addComponent(ConditionComponent(
          entityId = 2,
          conditionValues = attackerCondition
      ))
    }


    val ctx = EntityBattleContext(
        attackElement = Element.FIRE,
        defenderElement = Element.FIRE,
        weaponAtk = 10f,
        damageVariables = DamageVariables(),
        usedAttack = BattleAttack(
            10,
            10,
            10,
            AttackType.MAGIC,
            false
        ),
        attacker = attacker,
        defender = defender
    )
    val dmg = sut.calculateDamage(ctx)

    println(dmg)
  }
}