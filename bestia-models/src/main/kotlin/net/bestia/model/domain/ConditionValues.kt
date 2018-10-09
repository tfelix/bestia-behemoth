package net.bestia.model.domain

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable
import javax.persistence.Embeddable

/**
 * Status values contain all variable status values of a bestia. Usually these
 * values have to be saved in some way to the bestia entity since its current
 * value must be persisted.
 *
 * @author Thomas Felix
 */
@Embeddable
class ConditionValues(
    /**
     * Sets the current health value. The value can not be less then 0 and not
     * bigger then the current max value.
     */
    @get:JsonProperty("chp")
    var currentHealth: Int = 0,

    /**
     * Current maximum HP value.
     */
    @get:JsonProperty("mhp")
    var maxHealth: Int = 0,


    /**
     * Sets the current mana value. The value can not be less then 0 and not
     * bigger then the current max value.
     */
    @get:JsonProperty("cmana")
    var currentMana: Int = 0,

    /**
     * Returns the max mana.
     */
    @get:JsonProperty("mmana")
    var maxMana: Int = 0
) : Serializable {

  /**
   * Sets the object to the values from the given argument.
   *
   * @param rhs
   * The object to set all the local values to.
   */
  fun set(rhs: ConditionValues) {
    maxHealth = rhs.maxHealth
    maxMana = rhs.maxMana
    currentHealth = rhs.currentHealth
    currentMana = rhs.currentMana
  }

  /**
   * This will add or subtract HP from the current HP (depending if the
   * argument is positive or negative). Will return TRUE if this does NOT
   * lower the current HP below 1. FALSE otherwise.
   *
   * @param hp The value to subtract from current mana value. Must be
   * positive.
   */
  fun addHealth(hp: Int) {
    currentHealth += hp
  }

  /**
   * This will add or subtract Mana from the current Mana (depending if the
   * argument is positive or negative). Will return TRUE if this does NOT
   * lower the current Mana below 1. FALSE otherwise.
   *
   * @param mana The value to subtract from current mana value. Must be
   * positive.
   */
  fun addMana(mana: Int) {
    currentMana += mana
  }
}
