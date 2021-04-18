package net.bestia.zoneserver.battle

import net.bestia.model.bestia.ConditionValues
import net.bestia.model.entity.StatusBasedValues
import org.springframework.stereotype.Service

@Service
class RegenerationService {

  data class ConditionIncrements(
      var manaIncrement: Float = 0f,
      var healthIncrement: Float = 0f,
      var staminaIncrement: Float = 0f
  )

  fun addIncrements(
      conditionValues: ConditionValues,
      statusBasedValues: StatusBasedValues,
      currentIncrements: ConditionIncrements
  ): ConditionValues {
    val hpRegenStaminaInfluenced = getStaminaInfluencedHPTick(conditionValues, statusBasedValues)
    val manaRegenStaminaInfluenced = getStaminaInfluencedManaTick(conditionValues, statusBasedValues)

    var healthIncrement = hpRegenStaminaInfluenced * REGENERATION_TICK_RATE_MS / 1000
    var manaIncrement = manaRegenStaminaInfluenced * REGENERATION_TICK_RATE_MS / 1000
    var staminaIncrement = statusBasedValues.staminaRegenRate * REGENERATION_TICK_RATE_MS / 1000

    if (conditionValues.currentHealth == conditionValues.maxHealth) {
      healthIncrement = 0f
    }
    if (conditionValues.currentMana == conditionValues.maxMana) {
      manaIncrement = 0f
    }
    if (conditionValues.currentStamina == conditionValues.maxStamina) {
      staminaIncrement = 0f
    }

    currentIncrements.healthIncrement += healthIncrement
    currentIncrements.manaIncrement += manaIncrement
    currentIncrements.staminaIncrement += staminaIncrement

    return transferIncrementsToCondition(currentIncrements, conditionValues)
  }

  private fun transferIncrementsToCondition(
      currentIncrements: ConditionIncrements,
      conditionValues: ConditionValues
  ): ConditionValues {
    val hpRound = currentIncrements.healthIncrement.toInt()
    currentIncrements.healthIncrement -= hpRound.toFloat()

    val manaRound = currentIncrements.manaIncrement.toInt()
    currentIncrements.manaIncrement -= manaRound.toFloat()

    val staminaRound = currentIncrements.staminaIncrement.toInt()
    currentIncrements.staminaIncrement -= staminaRound.toFloat()

    val updatedConditionValues = conditionValues.copy(
        currentMana = conditionValues.currentMana + manaRound,
        currentHealth = conditionValues.currentHealth + hpRound,
        currentStamina = conditionValues.currentStamina + staminaRound
    )

    return updatedConditionValues
  }

  private fun getStaminaPerc(conValues: ConditionValues): Float {
    return conValues.currentStamina.toFloat() / conValues.maxStamina
  }

  /**
   * Returns the mana value ticked per regeneration step. Note that this value
   * might be smaller then 1. We use this to save the value between the ticks
   * until we have at least 1 mana and can add this to the user status.
   *
   * @param entity The entity.
   * @return The ticked mana value.
   */
  private fun getStaminaInfluencedManaTick(
      conValues: ConditionValues,
      statusBasedValues: StatusBasedValues
  ): Float {
    val staminaPerc = getStaminaPerc(conValues)

    return when {
      staminaPerc < 0.1f -> 0f
      staminaPerc < 0.5f -> conValues.maxMana * -0.03f
      else -> statusBasedValues.manaRegenRate
    }
  }

  /**
   * Returns the health value ticked per regeneration step. Note that this
   * value might be smaller then 1. We use this to save the value between the
   * ticks until we have at least 1 health and can add this to the user
   * status.
   *
   * @param entity The entity.
   * @return The ticked health value.
   */
  private fun getStaminaInfluencedHPTick(
      conValues: ConditionValues,
      statusBasedValues: StatusBasedValues
  ): Float {
    val staminaPerc = getStaminaPerc(conValues)
    return when {
      staminaPerc < 0.1f -> 0f
      staminaPerc < 0.5f -> conValues.maxHealth * -0.03f
      else -> statusBasedValues.hpRegenRate / 1000 * 8000
    }
  }

  companion object {
    /**
     * How often the regeneration should tick for each entity.
     */
    const val REGENERATION_TICK_RATE_MS = 2000L
  }
}