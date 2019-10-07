package net.bestia.model.bestia

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable
import javax.persistence.Embeddable

/**
 * Status values contain all variable status values of a Bestia. Usually these
 * values have to be saved in some way to the Bestia entity since its current
 * value must be persisted.
 *
 * @author Thomas Felix
 */
@Embeddable
data class ConditionValues(
    /**
     * Sets the current health value. The value can not be less then 0 and not
     * bigger then the current max value.
     */
    val currentHealth: Int = 0,

    /**
     * Current maximum HP value.
     */
    val maxHealth: Int = 0,

    /**
     * Sets the current mana value. The value can not be less then 0 and not
     * bigger then the current max value.
     */
    val currentMana: Int = 0,

    /**
     * Returns the max mana.
     */
    val maxMana: Int = 0,

    /**
     * Sets the current mana value. The value can not be less then 0 and not
     * bigger then the current max value.
     */
    val currentStamina: Int = 0,

    /**
     * Returns the max mana.
     */
    val maxStamina: Int = 0
) : Serializable {

  /**
   * This will add or subtract HP from the current HP (depending if the
   * argument is positive or negative). Will return TRUE if this does NOT
   * lower the current HP below 1. FALSE otherwise.
   *
   * @param hp The value to subtract from current mana value. Must be
   * positive.
   */
  fun addHealth(hp: Int): ConditionValues {
    return copy(currentHealth = currentHealth + hp)
  }

  /**
   * This will add or subtract Mana from the current Mana (depending if the
   * argument is positive or negative). Will return TRUE if this does NOT
   * lower the current Mana below 1. FALSE otherwise.
   *
   * @param mana The value to subtract from current mana value. Must be
   * positive.
   */
  fun addMana(mana: Int): ConditionValues {
    return copy(currentMana = currentMana + mana)
  }

  fun setMaximumValues(
      maxHp: Int,
      maxMana: Int,
      maxStamina: Int
  ): ConditionValues {
    return ConditionValues(
        maxHealth = maxHp,
        maxMana = maxMana,
        maxStamina = maxStamina,
        currentHealth = Math.min(maxHp, currentHealth),
        currentMana = Math.min(maxMana, currentMana),
        currentStamina = Math.min(maxStamina, currentStamina)
    )
  }

  fun updateConditionValues(
      conditionValues: ConditionValues = ConditionValues(),
      level: Int = 1,
      bVals: BaseValues,
      iVals: BaseValues = BaseValues.NULL_VALUES,
      eVals: BaseValues = BaseValues.NULL_VALUES
  ): ConditionValues {
    val maxHp = bVals.hp * 2 + iVals.hp + eVals.hp / 4 * level / 100 + 10 + level
    val maxMana = bVals.mana * 2 + iVals.mana + eVals.mana / 4 * level / 100 + 10 + level * 2
    val maxStamina = bVals.stamina * 2 + iVals.stamina + eVals.stamina / 4 * level / 100 + 10 + level

    return conditionValues.setMaximumValues(maxHp, maxMana, maxStamina)
  }
}
