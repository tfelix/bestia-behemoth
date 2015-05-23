package net.bestia.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrimaryKeyJoinColumn;

@Entity
@PrimaryKeyJoinColumn(name="bestia_id")
public class NPCBestia extends Bestia {
	
	
	private int gold;
	private int expGained;
	private int level;
	private boolean isBoss;
	/**
	 * Script which will be attached to this bestia.
	 */
	private String scriptExec;
	
	
	public int getGold() {
		return gold;
	}
	
	public int getExpGained() {
		return expGained;
	}
	
	public int getLevel() {
		return level;
	}
	
	public boolean isBoss() {
		return isBoss;
	}
	
	public String getScriptExec() {
		return scriptExec;
	}

}
