package net.bestia.model.battle;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple class for damage representation. The damage is tied to an entity.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Damage implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("dmg")
	private int damage;

	@JsonProperty("t")
	private DamageType type;

	/**
	 * Damage ctor.
	 * 
	 * @param damage
	 * @param type
	 */
	public Damage(int damage, DamageType type) {
		if (damage < 0) {
			throw new IllegalArgumentException("Damage can not be negative.");
		}

		this.setDamage(damage);
		this.setType(type);
	}

	/**
	 * Constructs a damage object of type hit with the given amount of damage.
	 * 
	 * @param uuid
	 *            Entity UUID to get hit by the damage.
	 * @param hitAmount
	 *            Amount of damage taken.
	 * @return The damage object.
	 */
	public static Damage getHit(int hitAmount) {
		return new Damage(hitAmount, DamageType.HIT);
	}

	/**
	 * Constructs a damage object of type heal with the given amount of heal.
	 * 
	 * @param uuid
	 *            Entity UUID to get hit by the damage.
	 * @param hitAmount
	 *            Amount of damage taken.
	 * @return The damage object.
	 */
	public static Damage getHeal(int healAmount) {
		return new Damage(healAmount, DamageType.HEAL);
	}

	/**
	 * Constructs a damage object of type miss.
	 * 
	 * @param uuid
	 *            Entity UUID to get hit by the damage miss.
	 * @return The damage object.
	 */
	public static Damage getMiss() {
		return new Damage(0, DamageType.MISS);
	}

	/**
	 * Constructs a damage object of type critical with the given amount of
	 * damage.
	 * 
	 * @param uuid
	 *            Entity UUID to get hit by the damage.
	 * @param hitAmount
	 *            Amount of damage taken.
	 * @return The damage object.
	 */
	public static Damage getCrit(int hitAmount) {
		final Damage d = getHit(hitAmount);
		d.setType(DamageType.CRITICAL);
		return d;
	}

	public int getDamage() {
		return damage;
	}

	public void setDamage(int damage) {
		if (damage < 0) {
			throw new IllegalArgumentException("Damage must be postive. Was negative.");
		}
		this.damage = damage;
	}

	public DamageType getType() {
		return type;
	}

	public void setType(DamageType type) {
		this.type = type;
	}

	@Override
	public String toString() {
		return String.format("Damage[a: %d, t: %s]", damage, type.toString());
	}
}
