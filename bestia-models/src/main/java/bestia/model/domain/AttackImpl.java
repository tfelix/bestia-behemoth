package bestia.model.domain;

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
public class AttackImpl implements Serializable, Attack {

	@Transient
	private static AttackImpl defaultMeleeAttack;

	static {
		defaultMeleeAttack = new AttackImpl();
		defaultMeleeAttack.id = DEFAULT_MELEE_ATTACK_ID;
		defaultMeleeAttack.databaseName = "default_melee_attack";
		defaultMeleeAttack.strength = 5;
		defaultMeleeAttack.element = Element.NORMAL;
		defaultMeleeAttack.manaCost = 0;
		defaultMeleeAttack.range = 1;
		defaultMeleeAttack.lineOfSight = true;
		defaultMeleeAttack.type = AttackType.MELEE_PHYSICAL;
		defaultMeleeAttack.target = AttackTarget.ENEMY_ENTITY;
		defaultMeleeAttack.casttime = 0;
		defaultMeleeAttack.cooldown = 1500;
	}

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
	 * Flag tells if the attack has a script which needs to get executed upon
	 * execution.
	 */
	private boolean hasScript;

	/**
	 * Range of the attack. Range is a mysql reserved word, so quots are needed.
	 */
	@JsonProperty("r")
	@Column(name = "atkRange", nullable = false)
	private int range;

	/**
	 * Check if a line of sight to the target is necessairy.
	 */
	@JsonProperty("los")
	private boolean lineOfSight;

	@Enumerated(EnumType.STRING)
	@JsonProperty("ty")
	private AttackType type;

	/**
	 * Casttime in ms. 0 means it is instant.
	 */
	@JsonProperty("ct")
	private int casttime;

	@JsonProperty("cd")
	private int cooldown;

	/**
	 * Shows if there is a special indicator when the attack is activated to be
	 * shown.
	 */
	@JsonProperty("i")
	private String indicator;

	@JsonProperty("a")
	private String animation;

	@Enumerated(EnumType.STRING)
	@JsonProperty("t")
	private AttackTarget target;

	public AttackImpl() {
		// no op.
	}

	public AttackImpl(String databaseName) {
		this.databaseName = databaseName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IAttack#getTarget()
	 */
	@Override
	public AttackTarget getTarget() {
		return target;
	}

	public void setTarget(AttackTarget target) {
		this.target = target;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IAttack#getDatabaseName()
	 */
	@Override
	public String getDatabaseName() {
		return databaseName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IAttack#getStrength()
	 */
	@Override
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IAttack#getElement()
	 */
	@Override
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IAttack#getManaCost()
	 */
	@Override
	public int getManaCost() {
		return manaCost;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IAttack#needsLineOfSight()
	 */
	@Override
	public boolean needsLineOfSight() {
		return lineOfSight;
	}

	/**
	 * @param manaCost
	 *            the manaCost to set
	 */
	public void setManaCost(int manaCost) {
		this.manaCost = manaCost;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IAttack#getCasttime()
	 */
	@Override
	public int getCasttime() {
		return casttime;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IAttack#getRange()
	 */
	@Override
	public int getRange() {
		return range;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IAttack#getCooldown()
	 */
	@Override
	public int getCooldown() {
		return cooldown;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IAttack#getId()
	 */
	@Override
	public int getId() {
		return id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.model.domain.IAttack#getIndicator()
	 */
	@Override
	public String getIndicator() {
		return indicator;
	}

	@Override
	public AttackType getType() {
		return type;
	}
	

	@Override
	public boolean hasScript() {
		return hasScript;
	}

	/**
	 * Each bestia has this one default attack which costs no mana and can be
	 * used anytime.
	 * 
	 * @return Specialized default melee attack.
	 */
	public static Attack getDefaultMeleeAttack() {
		return defaultMeleeAttack;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((databaseName == null) ? 0 : databaseName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AttackImpl other = (AttackImpl) obj;
		if (databaseName == null) {
			if (other.databaseName != null)
				return false;
		} else if (!databaseName.equals(other.databaseName))
			return false;
		return true;
	}
}
