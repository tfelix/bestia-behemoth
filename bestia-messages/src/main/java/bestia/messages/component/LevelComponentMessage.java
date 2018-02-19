package bestia.messages.component;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LevelComponentMessage implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	@JsonProperty("lv")
	private final int level;

	public LevelComponentMessage(int level) {
		
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