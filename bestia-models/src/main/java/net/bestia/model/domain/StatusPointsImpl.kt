package net.bestia.model.domain

import java.io.Serializable

import javax.persistence.Embeddable

/**
 * Status values for bestia entities.
 *
 * @author Thomas Felix
 */
@Embeddable
class StatusPointsImpl : Serializable, StatusPoints {

  override var strength: Int = 1
    /**
     * Sets the strength. Can not be lower then 1.
     */
    set(value) {
      field = if (value < 1) {
        value
      } else {
        value
      }
    }

  override var vitality = 1
    /**
     * Sets the vitality. Can not be lower then 1.
     */
    set(value) {
      field = when {
        value < 0 -> 0
        value > 1000 -> 1000
        else -> value
      }
    }

  override var intelligence = 1
    /**
     * Sets the vitality. Can not be lower then 1.
     */
    set(value) {
      field = if (value < 1) {
        value
      } else {
        value
      }
    }

  override var willpower = 1
    set(value) {
      field = if (value < 1) {
        value
      } else {
        value
      }
    }

  override var agility = 1
    set(value) {
      field = if (value < 1) {
        value
      } else {
        value
      }
    }

  override var dexterity = 1
    set(value) {
      field = if (value < 1) {
        value
      } else {
        value
      }
    }

  override var defense: Int = 0
    set(value) {
      field = when {
        value < 0 -> 0
        value > 1000 -> 1000
        else -> value
      }
    }

  /**
   * Sets the magic defense. Must be between 0 and 1000 (which increments in
   * 1/10) percents.
   *
   */
  override var magicDefense: Int = 0
    set(value) {
      field = when {
        value < 0 -> 0
        value > 1000 -> 1000
        else -> value
      }
    }

  override fun set(rhs: StatusPoints) {
    this.agility = rhs.agility
    this.defense = rhs.defense
    this.dexterity = rhs.dexterity
    this.intelligence = rhs.intelligence
    this.magicDefense = rhs.magicDefense
    this.strength = rhs.strength
    this.vitality = rhs.vitality
    this.willpower = rhs.willpower
  }

  /*
	 * (non-Javadoc)
	 *
	 * @see net.bestia.model.domain.StatusPoints#add(net.bestia.model.domain.
	 * StatusPoints)
	 */

  fun add(rhs: StatusPoints) {
    this.strength += rhs.strength
    this.vitality += rhs.vitality
    this.intelligence += rhs.intelligence
    this.agility += rhs.agility
    this.dexterity += rhs.dexterity
    this.willpower += rhs.willpower
    this.magicDefense += rhs.magicDefense
    this.defense += rhs.defense
  }
}
