package net.bestia.core.game.model;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;

import net.bestia.core.game.battle.AttackBasedStatus;
import net.bestia.core.game.battle.Element;

@Entity
public class Attack {
	@Id
	private String databaseName;
	private int strength;
	private String name;
	@Enumerated(EnumType.STRING)
	private Element element;
	private int manaCost;
	@Enumerated(EnumType.STRING)
	private AttackBasedStatus basedStatus;
	
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
	 * @param strength the strength to set
	 */
	public void setStrength(int strength) {
		this.strength = strength;
	}
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * @return the element
	 */
	public Element getElement() {
		return element;
	}
	
	/**
	 * @param element the element to set
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
	 * @param manaCost the manaCost to set
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
	 * @param basedStatus the basedStatus to set
	 */
	public void setBasedStatus(AttackBasedStatus basedStatus) {
		this.basedStatus = basedStatus;
	}
}
