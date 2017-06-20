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
		this.currentHealth = currentHealth;
	}

	public void setCurrentMana(int currentMana) {
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
}
