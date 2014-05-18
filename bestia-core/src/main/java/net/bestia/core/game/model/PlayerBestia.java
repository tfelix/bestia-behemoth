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
	private int currentHp;
	private int maxHp;
	private int currentMana;
	private int maxMana;
	private int exp;
	private String name;
	private Date traveltime;
	@AttributeOverrides({
			@AttributeOverride(name = "mapDbName", column = @Column(name = "saveMapDbName")),
			@AttributeOverride(name = "x", column = @Column(name = "saveX")),
			@AttributeOverride(name = "y", column = @Column(name = "saveY")), })
	@Embedded
	private Location savePosition;

	@Embedded
	private Location currentPosition;
	@Enumerated(EnumType.STRING)
	private PVPMode pvpMode;
	@ManyToOne
	@JoinColumn(name="account_id")
	private Account owner;
	@Embedded
	private StatusPoint individualValue;
	@Embedded
	private StatusPoint effortValue;

	public int getCurrentHp() {
		return currentHp;
	}

	public void setCurrentHp(int currentHp) {
		this.currentHp = currentHp;
	}

	public int getMaxHp() {
		return maxHp;
	}

	public void setMaxHp(int maxHp) {
		this.maxHp = maxHp;
	}

	public int getCurrentMana() {
		return currentMana;
	}

	public void setCurrentMana(int currentMana) {
		this.currentMana = currentMana;
	}

	public int getMaxMana() {
		return maxMana;
	}

	public void setMaxMana(int maxMana) {
		this.maxMana = maxMana;
	}

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
	
	public StatusPoint getIndividualValue() {
		return individualValue;
	}
	
	public StatusPoint getEffortValue() {
		return effortValue;
	}
}
