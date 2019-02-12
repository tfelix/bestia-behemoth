package net.bestia.model.bestia

import java.io.Serializable
import java.lang.IllegalArgumentException

import javax.persistence.Embeddable

/**
 * Status values for Bestia entities.
 *
 * @author Thomas Felix
 */
@Embeddable
data class BasicStatusValues(
    override val strength: Int = 1,
    override val vitality: Int = 1,
    override val intelligence: Int = 1,
    override val willpower: Int = 1,
    override val agility: Int = 1,
    override val dexterity: Int = 1,
    /**
     * Sets the defense. Must be between 0 and 1000 (which increments in
     * 1/10) percents.
     */
    override val physicalDefense: Int = 0,
    /**
     * Sets the magic defense. Must be between 0 and 1000 (which increments in
     * 1/10) percents.
     */
    override val magicDefense: Int = 0
) : Serializable, StatusValues {
  init {
    if (strength < 1) {
      throw IllegalArgumentException("Strength can not be less than 1")
    }
    // TODO Other checks
  }
}