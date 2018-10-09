package net.bestia.model.domain

import java.io.Serializable

import javax.persistence.Embeddable

/**
 * Status values for Bestia entities.
 *
 * @author Thomas Felix
 */
@Embeddable
data class StatusPointsImpl(
    override var strength: Int = 1,
    override var vitality: Int = 1,
    override var intelligence: Int = 1,
    override var willpower: Int = 1,
    override var agility: Int = 1,
    override var dexterity: Int = 1,
    /**
     * Sets the defense. Must be between 0 and 1000 (which increments in
     * 1/10) percents.
     */
    override var defense: Int = 0,
    /**
     * Sets the magic defense. Must be between 0 and 1000 (which increments in
     * 1/10) percents.
     */
    override var magicDefense: Int = 0
) : Serializable, StatusPoints {

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
