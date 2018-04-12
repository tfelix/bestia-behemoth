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
class ConditionValues : Serializable {

  /**
   * Sets the current health value. The value can not be less then 0 and not
   * bigger then the current max value.
   */
  @get:JsonProperty("chp")
  var currentHealth: Int = 0
    set(value) {
      field = when {
        value > maxHealth -> maxHealth
        field < 0 -> 0
        else -> value
      }
    }

  /**
   * Current maximum HP value.
   *
   * @return current maximum HP.
   */
  @get:JsonProperty("mhp")
  var maxHealth: Int = 0
    set(value) {
      field = value
      if (value < currentHealth) {
        currentHealth = value
      }
    }

  /**
   * @return The current mana.
   */
  /**
   * Sets the current mana value. The value can not be less then 0 and not
   * bigger then the current max value.
   */
  @get:JsonProperty("cmana")
  var currentMana: Int = 0
    set(value) {
      field = when {
        value > maxMana -> maxMana
        field < 0 -> 0
        else -> value
      }
    }

  /**
   * Returns the max mana.
   *
   * @return Max mana.
   */
  @get:JsonProperty("mmana")
  var maxMana: Int = 0
    set(value) {
      field = value
      if (value < currentMana) {
        currentMana = value
      }
    }

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
