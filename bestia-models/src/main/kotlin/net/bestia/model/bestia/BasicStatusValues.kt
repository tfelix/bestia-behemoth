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
    require(strength >= 1) { "Strength can not be less than 1" }
    require(vitality >= 1) { "Vitality can not be less than 1" }
    require(intelligence >= 1) { "Intelligence can not be less than 1" }
    require(willpower >= 1) { "Willpower can not be less than 1" }
    require(agility >= 1) { "Agility can not be less than 1" }
    require(dexterity >= 1) { "Dexterity can not be less than 1" }
  }

  operator fun plus(rhs: StatusValues): BasicStatusValues {
    return BasicStatusValues(
        strength = strength + rhs.strength,
        vitality = vitality + rhs.vitality,
        intelligence = intelligence + rhs.intelligence,
        willpower = willpower + rhs.willpower,
        agility = agility + rhs.agility,
        dexterity = dexterity + rhs.dexterity,
        physicalDefense = physicalDefense + rhs.physicalDefense,
        magicDefense = magicDefense + rhs.magicDefense
    )
  }
}