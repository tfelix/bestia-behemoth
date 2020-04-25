package net.bestia.model.bestia

import java.io.Serializable
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
    override val dexterity: Int = 1
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
        dexterity = dexterity + rhs.dexterity
    )
  }
}