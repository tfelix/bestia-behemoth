package net.bestia.messages.component;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LevelComponent implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	@JsonProperty("lv")
	private final int level;

	public LevelComponent(int level) {
		
		this.level = level;
	}
	
	public int getLevel() {
		return level;
	}
	
	@Override
	public String toString() {
		return String.format("LevelComponent[lv: %d]", getLevel());
	}
}