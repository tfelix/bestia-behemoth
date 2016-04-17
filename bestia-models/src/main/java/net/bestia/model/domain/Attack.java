package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "attacks")
public class Attack implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	private int id;

	@Column(name = "attack_db_name", unique = true, nullable = false)
	@JsonProperty("adbn")
	private String databaseName;

	@JsonProperty("str")
	private int strength;

	@Enumerated(EnumType.STRING)
	@JsonProperty("ele")
	@Column(nullable = false)
	private Element element;

	@JsonProperty("m")
	private int manaCost;

	/**
	 * Range of the attack. Range is a mysql reserved word, so quots are needed.
	 */
	@JsonProperty("r")
	@Column(name = "atkRange")
	private int range;

	@JsonProperty("los")
	private boolean lineOfSight;

	@Enumerated(EnumType.STRING)
	@JsonProperty("bs")
	private AttackBasedStatus basedStatus;

	@JsonProperty("ct")
	private int casttime;

	@JsonProperty("cd")
	private int cooldown;

	@JsonProperty("i")
	private String indicator;

	public Attack() {
		// no op.
	}

	public Attack(String databaseName) {
		this.databaseName = databaseName;
	}

	/**
	 * @return the attackDbName
	 */
	public String getDatabaseName() {
		return databaseName;
	}

	/**
	 * @return the strength
	 */
	public int getStrength() {
		return strength;
	}

	/**
	 * @param strength
	 *            the strength to set
	 */
	public void setStrength(int strength) {
		this.strength = strength;
	}

	/**
	 * @return the element
	 */
	public Element getElement() {
		return element;
	}

	/**
	 * @param element
	 *            the element to set
	 */
	public void setElement(Element element) {
		this.element = element;
	}

	/**
	 * @return the manaCost
	 */
	public int getManaCost() {
		return manaCost;
	}

	/**
	 * Returns if the attack requires a line of sight.
	 * 
	 * @return TRUE of the attack requires a line of sight to the enemy. FALSE
	 *         otherwise.
	 */
	public boolean getLineOfSight() {
		return lineOfSight;
	}

	/**
	 * @param manaCost
	 *            the manaCost to set
	 */
	public void setManaCost(int manaCost) {
		this.manaCost = manaCost;
	}

	/**
	 * @return the basedStatus
	 */
	public AttackBasedStatus getBasedStatus() {
		return basedStatus;
	}

	/**
	 * @param basedStatus
	 *            the basedStatus to set
	 */
	public void setBasedStatus(AttackBasedStatus basedStatus) {
		this.basedStatus = basedStatus;
	}

	/**
	 * Base casttime of an attack in ms.
	 * 
	 * @return Cast time of an attack in ms.
	 */
	public int getCasttime() {
		return casttime;
	}

	/**
	 * Returns the range of the attack in tiles.
	 * 
	 * @return The range of the attack in tiles.
	 */
	public int getRange() {
		return range;
	}

	/**
	 * Base cooldown time after the attack can be used again in ms.
	 * 
	 * @return Cooldown of the attack in ms.
	 */
	public int getCooldown() {
		return cooldown;
	}

	public Integer getId() {
		return id;
	}

	public String getIndicator() {
		return indicator;
	}
}
