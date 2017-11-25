package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Objects;

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
public class ConditionValues implements Serializable {

	private static final long serialVersionUID = 1L;

	private int currentHealth;
	private int currentMana;
	private int maxHealth;
	private int maxMana;

	/**
	 * @return The current health.
	 */
	@JsonProperty("chp")
	public int getCurrentHealth() {
		return currentHealth;
	}

	/**
	 * Current maximum HP value.
	 * 
	 * @return current maximum HP.
	 */
	@JsonProperty("mhp")
	public int getMaxHealth() {
		return maxHealth;
	}

	/**
	 * @return The current mana.
	 */
	@JsonProperty("cmana")
	public int getCurrentMana() {
		return currentMana;
	}

	/**
	 * Returns the max mana.
	 * 
	 * @return Max mana.
	 */
	@JsonProperty("mmana")
	public int getMaxMana() {
		return maxMana;
	}

	/**
	 * Sets the current health value. The value can not be less then 0 and not
	 * bigger then the current max value.
	 * 
	 * @param currentHealth
	 *            The new health value.
	 */
	public void setCurrentHealth(int currentHealth) {
		if (currentHealth < 0) {
			currentHealth = 0;
		}

		if (currentHealth > maxHealth) {
			currentHealth = maxHealth;
		}

		this.currentHealth = currentHealth;
	}

	/**
	 * Sets the current mana value. The value can not be less then 0 and not
	 * bigger then the current max value.
	 * 
	 * @param currentMana
	 *            New current mana value.
	 */
	public void setCurrentMana(int currentMana) {
		if (currentMana < 0) {
			currentMana = 0;
		}

		if (currentMana > maxMana) {
			currentMana = maxMana;
		}

		this.currentMana = currentMana;
	}

	public void setMaxHealth(int maxHealth) {
		this.maxHealth = maxHealth;

		if (maxHealth > currentHealth) {
			setCurrentHealth(maxHealth);
		}
	}

	public void setMaxMana(int maxMana) {
		this.maxMana = maxMana;

		if (maxMana > currentMana) {
			setCurrentMana(maxMana);
		}
	}

	/**
	 * Sets the object to the values from the given argument.
	 * 
	 * @param rhs
	 *            The object to set all the local values to.
	 */
	public void set(ConditionValues rhs) {

		setMaxHealth(rhs.getMaxHealth());
		setMaxMana(rhs.getMaxMana());
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

	@Override
	public String toString() {
		return String.format("SV[mana: %d, hp: %d]", getCurrentMana(), getCurrentHealth());
	}

	@Override
	public int hashCode() {
		return Objects.hash(currentHealth, currentMana, maxHealth, maxMana);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConditionValues other = (ConditionValues) obj;
		if (currentHealth != other.currentHealth)
			return false;
		if (currentMana != other.currentMana)
			return false;
		if (maxHealth != other.maxHealth)
			return false;
		if (maxMana != other.maxMana)
			return false;
		return true;
	}
}
