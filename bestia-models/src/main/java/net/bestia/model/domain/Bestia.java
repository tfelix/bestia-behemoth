package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "bestias")
@PrimaryKeyJoinColumn(name = "bestia_id")
public class Bestia implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	private int id;

	@Column(name = "bestia_db_name")
	@JsonProperty("bdbn")
	private String databaseName;

	@Enumerated(EnumType.STRING)
	@JsonProperty("ele")
	private Element element;

	@JsonProperty("img")
	private String image = "";
	@JsonProperty("s")
	private String sprite = "";

	@JsonIgnore
	private int gold;
	@JsonIgnore
	private int expGained;
	@JsonIgnore
	private int level;
	
	private boolean isBoss;

	/*-
	 * This values is added to each player bestia when it kill another bestia from this kind until the maximum amount
	 * of effort value is reached. The distribution of the effort values is calculated by the distribution of the real
	 * status values of a bestia and depending on its level.
	 * 
	 * Level  1 - 25: Totel EVs:  1
	 * Level 26 - 50: Totel EVs:  2
	 * Level 51 - 75: Totel EVs:  3
	 * Level 76 - 100: Totel EVs: 4
	 * 
	 */
	@Transient
	@JsonIgnore
	private BaseValues effortValues;

	/**
	 * The status points are calculated based on the base stats and the level aswell. But in contrast to the players
	 * wild bestias gain a small boost in their stat calculation in order to compensate for missing equipment. Their
	 * armor and special armor is also saved as a fixed value in the database.
	 */
	@Transient
	@JsonIgnore
	private StatusPoints statusValues;

	/**
	 * Override the names because the are the same like in status points. Both entities are embedded so we need
	 * individual column names. This values is added to each bestia when it kill another bestia from this kind.
	 */
	@Embedded
	@AttributeOverrides({ @AttributeOverride(name = "hp", column = @Column(name = "bHp")),
			@AttributeOverride(name = "mana", column = @Column(name = "bMana")),
			@AttributeOverride(name = "atk", column = @Column(name = "bAtk")),
			@AttributeOverride(name = "def", column = @Column(name = "bDef")),
			@AttributeOverride(name = "spAtk", column = @Column(name = "bSpAtk")),
			@AttributeOverride(name = "spDef", column = @Column(name = "bSpDef")),
			@AttributeOverride(name = "spd", column = @Column(name = "bSpd")) })
	@JsonIgnore
	private BaseValues baseValues;
	
	/**
	 * Script which will be attached to this bestia.
	 */
	@JsonIgnore
	private String scriptExec;

	public Bestia() {

		// calculateEffortValues();
	}

	/**
	 * Calculates the effort values depending on its level and the base values.
	 */
	private void calculateEffortValues() {
		effortValues = new BaseValues();

		int maxEffortVal;
		if (level <= 25) {
			maxEffortVal = 1;
		} else if (level <= 50) {
			maxEffortVal = 2;
		} else if (level <= 75) {
			maxEffortVal = 3;
		} else {
			maxEffortVal = 4;
		}

		// Calculate total amount of base values and distribute accordingly.
		final float baseMax = baseValues.getAtk() + baseValues.getDef() + baseValues.getHp() + baseValues.getMana()
				+ baseValues.getSpAtk() + baseValues.getSpd() + baseValues.getSpDef();

		final int evAtk = Math.round(maxEffortVal * (baseValues.getAtk() / baseMax));
		final int evDef = Math.round(maxEffortVal * (baseValues.getDef() / baseMax));
		final int evHp = Math.round(maxEffortVal * (baseValues.getHp() / baseMax));
		final int evMana = Math.round(maxEffortVal * (baseValues.getMana() / baseMax));

		final int evSpAtk = Math.round(maxEffortVal * (baseValues.getSpAtk() / baseMax));
		final int evSpDef = Math.round(maxEffortVal * (baseValues.getSpDef() / baseMax));
		final int evSpd = Math.round(maxEffortVal * (baseValues.getSpd() / baseMax));

		// Set the values.
		effortValues.setAtk(evAtk);
		effortValues.setDef(evDef);
		effortValues.setHp(evHp);
		effortValues.setMana(evMana);
		effortValues.setSpAtk(evSpAtk);
		effortValues.setSpDef(evSpDef);
		effortValues.setSpd(evSpd);
	}

	public BaseValues getBaseValues() {
		return baseValues;
	}

	public String getImage() {
		return image;
	}

	public String getSprite() {
		return sprite;
	}

	public int getGold() {
		return gold;
	}

	public int getExpGained() {
		return expGained;
	}

	public int getLevel() {
		return level;
	}

	@JsonIgnore
	public boolean isBoss() {
		return isBoss;
	}

	public String getScriptExec() {
		return scriptExec;
	}

	/**
	 * Returns the NPC bestias effort values which will be granted if the bestia was killed by a player.
	 * 
	 * @return The earned effort values if a bestia was killed by a player.
	 */
	public BaseValues getEffortValues() {
		if (effortValues == null) {
			calculateEffortValues();
		}
		return effortValues;
	}

}
