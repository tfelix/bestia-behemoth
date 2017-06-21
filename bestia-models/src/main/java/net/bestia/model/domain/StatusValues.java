package net.bestia.model.domain;

import javax.persistence.Embeddable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Status values contain all variable status values of a bestia. Usually these
 * values have to be saved in some way to the bestia entity since its current
 * value must be persisted.
 * 
 * @author Thomas Felix
 *
 */
@Embeddable
public class StatusValues {

	private int currentHealth;
	private int currentMana;

	@JsonProperty("chp")
	public int getCurrentHealth() {
		return currentHealth;
	}

	@JsonProperty("cmana")
	public int getCurrentMana() {
		return currentMana;
	}

	public void setCurrentHealth(int currentHealth) {
		if (currentHealth < 0) {
			currentHealth = 0;
		}

		this.currentHealth = currentHealth;
	}

	public void setCurrentMana(int currentMana) {
		if (currentMana < 0) {
			currentMana = 0;
		}

		this.currentMana = currentMana;
	}

	/**
	 * Sets the object to the values from the given argument.
	 * 
	 * @param rhs
	 *            The object to set all the local values to.
	 */
	public void set(StatusValues rhs) {

		setCurrentHealth(rhs.getCurrentHealth());
		setCurrentMana(rhs.getCurrentMana());

	}

	/**
	 * This will add or subtract HP from the current HP (depending if the
	 * argument is positive or negative). Will return TRUE if this does NOT
	 * lower the current HP below 1. FALSE otherwise.
	 * 
	 * @param addHp
	 *            The value to subtract from current mana value. Must be
	 *            positive.
	 */
	public void addHealth(int hp) {
		this.setCurrentHealth(this.currentHealth + hp);
	}

	/**
	 * This will add or subtract Mana from the current Mana (depending if the
	 * argument is positive or negative). Will return TRUE if this does NOT
	 * lower the current Mana below 1. FALSE otherwise.
	 * 
	 * @param addMana
	 *            The value to subtract from current mana value. Must be
	 *            positive.
	 */
	public void addMana(int mana) {
		this.setCurrentMana(this.currentMana + mana);
	}
}
