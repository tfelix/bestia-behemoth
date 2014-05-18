package net.bestia.core.game.model;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import net.bestia.core.game.battle.AttackBasedStatus;
import net.bestia.core.game.battle.Element;

@Entity
public class Attack {
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private int id;
	private String attackDbName;
	private int strength;
	private String name;
	private Element element;
	private int manaCost;
	@Enumerated(EnumType.STRING)
	private AttackBasedStatus basedStatus;
	
	/**
	 * @return the attackDbName
	 */
	public String getAttackDbName() {
		return attackDbName;
	}
	
	/**
	 * @param attackDbName the attackDbName to set
	 */
	public void setAttackDbName(String attackDbName) {
		this.attackDbName = attackDbName;
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
	
	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}
}
