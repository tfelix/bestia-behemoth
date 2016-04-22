package net.bestia.model.misc;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple class for damage representation. The damage is tied to an entity.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Damage implements Serializable {

	public enum DamageType {
		HEAL, MISS,

		/**
		 * Normal hit damage.
		 */
		HIT,

		/**
		 * This was a critical damage.
		 */
		CRITICAL,

		/**
		 * True damage will (in most cases) hit the bestia without modifications
		 * of status effects or equipments.
		 */
		TRUE
	}

	private static final long serialVersionUID = 1L;

	@JsonProperty("uuid")
	private String entityUUID;

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
	public Damage(String uuid, int damage, DamageType type) {
		if (uuid == null || uuid.isEmpty()) {
			throw new IllegalArgumentException("Uuid can not be null or empty.");
		}

		if (damage < 0) {
			throw new IllegalArgumentException("Damage can not be negative.");
		}

		this.setDamage(damage);
		this.setType(type);
		this.entityUUID = uuid;
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
	public static Damage getHit(String uuid, int hitAmount) {
		return new Damage(uuid, hitAmount, DamageType.HIT);
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
	public static Damage getHeal(String uuid, int healAmount) {
		return new Damage(uuid, healAmount, DamageType.HEAL);
	}

	/**
	 * Constructs a damage object of type miss.
	 * 
	 * @param uuid
	 *            Entity UUID to get hit by the damage miss.
	 * @return The damage object.
	 */
	public static Damage getMiss(String uuid) {
		return new Damage(uuid, 0, DamageType.MISS);
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
	public static Damage getCrit(String uuid, int hitAmount) {
		final Damage d = getHit(uuid, hitAmount);
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

	public String getEntityUUID() {
		return entityUUID;
	}

	public void setEntityUUID(String entityUUID) {
		if (entityUUID == null || entityUUID.isEmpty()) {
			throw new IllegalArgumentException("EntityUUID can not be null or empty.");
		}
		this.entityUUID = entityUUID;
	}

	@Override
	public String toString() {
		return String.format("Damage[a: %d, t: %s]", damage, type.toString());
	}

}
