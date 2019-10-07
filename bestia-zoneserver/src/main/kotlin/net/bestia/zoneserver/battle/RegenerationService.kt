package net.bestia.zoneserver.battle

import net.bestia.zoneserver.entity.component.ConditionComponent
import net.bestia.zoneserver.entity.component.StatusComponent

data class ConditionIncrements(
    var manaIncrement: Float = 0f,
    var healthIncrement: Float = 0f,
    var staminaIncrement: Float = 0f
)

class RegenerationService {

  fun addIncrements(currentIncrements: ConditionIncrements, statusComponent: StatusComponent) {
    val healthIncrement = statusComponent.statusBasedValues.hpRegenRate * REGENERATION_TICK_RATE_MS / 1000
    val manaIncrement = statusComponent.statusBasedValues.manaRegenRate * REGENERATION_TICK_RATE_MS / 1000
    val staminaIncrement = statusComponent.statusBasedValues.staminaRegenRate * REGENERATION_TICK_RATE_MS / 1000

    currentIncrements.healthIncrement += healthIncrement
    currentIncrements.manaIncrement += manaIncrement
    currentIncrements.staminaIncrement += staminaIncrement
  }

  fun transferIncrementsToCondition(
      currentIncrements: ConditionIncrements,
      conditionComponent: ConditionComponent
  ): ConditionComponent {
    val hpRound = currentIncrements.healthIncrement.toInt()
    currentIncrements.healthIncrement -= hpRound.toFloat()

    val manaRound = currentIncrements.manaIncrement.toInt()
    currentIncrements.manaIncrement -= manaRound.toFloat()

    val staminaRound = currentIncrements.staminaIncrement.toInt()
    currentIncrements.staminaIncrement -= staminaRound.toFloat()

    val updatedConditionValues = conditionComponent.conditionValues.copy(
        currentMana = conditionComponent.conditionValues.currentMana + manaRound,
        currentHealth = conditionComponent.conditionValues.currentHealth + hpRound,
        currentStamina = conditionComponent.conditionValues.currentStamina + staminaRound
    )

    return conditionComponent.copy(conditionValues = updatedConditionValues)
  }

  companion object {
    /**
     * How often the regeneration should tick for each entity.
     */
    const val REGENERATION_TICK_RATE_MS = 4000L
  }
}