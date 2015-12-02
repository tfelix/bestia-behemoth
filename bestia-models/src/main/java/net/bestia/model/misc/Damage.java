package net.bestia.model.misc;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Damage implements Serializable {

	public enum DamageType {
		HEAL,
		MISS,
		HIT
	}
	
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
		if(damage < 0) {
			throw new IllegalArgumentException("Damage can not be negative.");
		}
		
		this.setDamage(damage);
		this.setType(type);
	}
	
	public static Damage getHit(int hitAmount) {
		return new Damage(hitAmount, DamageType.HIT);
	}
	
	public static Damage getHeal(int healAmount) {
		return new Damage(healAmount, DamageType.HEAL);
	}
	
	public static Damage getMiss() {
		return new Damage(0, DamageType.MISS);
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
	
	@Override
	public String toString() {
		return String.format("Damage[a: %d, t: %s]", damage, type.toString());
	}

}
