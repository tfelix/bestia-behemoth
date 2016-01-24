package net.bestia.model.misc;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Damage implements Serializable {

	public enum DamageType {
		HEAL,
		MISS,
		HIT,
		CRITICAL
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
		if(damage < 0) {
			throw new IllegalArgumentException("Damage can not be negative.");
		}
		
		this.setDamage(damage);
		this.setType(type);
		this.entityUUID = uuid;
	}
	
	public static Damage getHit(String uuid, int hitAmount) {
		return new Damage(uuid, hitAmount, DamageType.HIT);
	}
	
	public static Damage getHeal(String uuid, int healAmount) {
		return new Damage(uuid, healAmount, DamageType.HEAL);
	}
	
	public static Damage getMiss(String uuid) {
		return new Damage(uuid, 0, DamageType.MISS);
	}

	public int getDamage() {
		return damage;
	}

	public void setDamage(int damage) {
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
		this.entityUUID = entityUUID;
	}
	
	@Override
	public String toString() {
		return String.format("Damage[a: %d, t: %s]", damage, type.toString());
	}

}
