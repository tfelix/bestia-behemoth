package net.bestia.core.game.model;

import java.sql.Date;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.core.game.battle.PVPMode;

/**
 * Entity for the PlayerBestias these are bestias which are directly controlled
 * by the player.
 * 
 * @author Thomas
 * 
 */
@Entity
public class PlayerBestia extends Bestia {
	@JsonProperty("e")
	private int exp;
	@JsonProperty("n")
	private String name;
	@JsonProperty("tt")
	private Date traveltime;
	@AttributeOverrides({
			@AttributeOverride(name = "mapDbName", column = @Column(name = "saveMapDbName")),
			@AttributeOverride(name = "x", column = @Column(name = "saveX")),
			@AttributeOverride(name = "y", column = @Column(name = "saveY")), })
	@Embedded
	@JsonProperty("sp")
	private Location savePosition;

	@Embedded
	@JsonProperty("cp")
	private Location currentPosition;
	@Enumerated(EnumType.STRING)
	@JsonProperty("pvp")
	private PVPMode pvpMode;
	@ManyToOne
	@JoinColumn(name="account_id")
	@JsonIgnore
	private Account owner;
	
	@AttributeOverrides({
		@AttributeOverride(name = "hp", column = @Column(name = "statCurHp")),
		@AttributeOverride(name = "maxHp", column = @Column(name = "statMaxHp")),
		@AttributeOverride(name = "mana", column = @Column(name = "statCurMana")),
		@AttributeOverride(name = "maxMana", column = @Column(name = "statMaxMana")),		
		@AttributeOverride(name = "atk", column = @Column(name = "statCurAtk")),
		@AttributeOverride(name = "def", column = @Column(name = "statCurDef")),
		@AttributeOverride(name = "spAtk", column = @Column(name = "statCurSpAtk")),
		@AttributeOverride(name = "spDef", column = @Column(name = "statCurSpDef")),
		@AttributeOverride(name = "spd", column = @Column(name = "statCurSpd")) })
	private StatusPoints statusPoints;
	
	/**
	 * Override the names because the are the same like in status points. Both
	 * entities are embedded so we need individual column names. This values is
	 * added to each bestia when it kill another bestia from this kind.
	 */
	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "hp", column = @Column(name = "evHp")),
			@AttributeOverride(name = "mana", column = @Column(name = "evMana")),
			@AttributeOverride(name = "atk", column = @Column(name = "evAtk")),
			@AttributeOverride(name = "def", column = @Column(name = "evDef")),
			@AttributeOverride(name = "spAtk", column = @Column(name = "evSpAtk")),
			@AttributeOverride(name = "spDef", column = @Column(name = "evSpDef")),
			@AttributeOverride(name = "spd", column = @Column(name = "evSpd")) })
	@JsonIgnore
	private BaseValues effortValues;
	
	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "hp", column = @Column(name = "ivHp")),
		@AttributeOverride(name = "mana", column = @Column(name = "ivMana")),
		@AttributeOverride(name = "atk", column = @Column(name = "ivAtk")),
		@AttributeOverride(name = "def", column = @Column(name = "ivDef")),
		@AttributeOverride(name = "spAtk", column = @Column(name = "ivSpAtk")),
		@AttributeOverride(name = "spDef", column = @Column(name = "ivSpDef")),
		@AttributeOverride(name = "spd", column = @Column(name = "ivSpd")) })
	@JsonIgnore
	private BaseValues individualValue;


	public int getExp() {
		return exp;
	}

	public void setExp(int exp) {
		this.exp = exp;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Date getTraveltime() {
		return traveltime;
	}

	public void setTraveltime(Date traveltime) {
		this.traveltime = traveltime;
	}

	public Location getSavePosition() {
		return savePosition;
	}

	public void setSavePosition(Location savePosition) {
		this.savePosition = savePosition;
	}

	public Location getCurrentPosition() {
		return currentPosition;
	}

	public void setCurrentPosition(Location currentPosition) {
		this.currentPosition = currentPosition;
	}

	public PVPMode getPvpMode() {
		return pvpMode;
	}

	public void setPvpMode(PVPMode pvpMode) {
		this.pvpMode = pvpMode;
	}
	
	public BaseValues getIndividualValue() {
		return individualValue;
	}
	
	public BaseValues getEffortValue() {
		return effortValues;
	}
}
