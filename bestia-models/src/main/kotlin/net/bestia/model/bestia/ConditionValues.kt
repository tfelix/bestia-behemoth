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
    @get:JsonProperty("chp")
    val currentHealth: Int = 0,

    /**
     * Current maximum HP value.
     */
    @get:JsonProperty("mhp")
    val maxHealth: Int = 0,


    /**
     * Sets the current mana value. The value can not be less then 0 and not
     * bigger then the current max value.
     */
    @get:JsonProperty("cmana")
    val currentMana: Int = 0,

    /**
     * Returns the max mana.
     */
    @get:JsonProperty("mmana")
    val maxMana: Int = 0
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
    return copy(currentHealth + hp)
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
    return copy(currentMana + mana)
  }

  fun setMaximumValues(maxHp: Int, maxMana: Int): ConditionValues {
    return ConditionValues(
        maxHealth = maxHp,
        maxMana = maxMana,
        currentHealth = Math.min(maxHp, currentHealth),
        currentMana = Math.min(maxMana, currentMana)
    )
  }
}
