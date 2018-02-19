package bestia.messages.component;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This level component messages contains the current EXP amount of the bestia
 * this is send to the owner of the bestia. The normal {@link LevelComponentMessage} is
 * send to all others which only contains the level.
 * 
 * @author Thomas Felix
 *
 */
public class LevelComponentExMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("lv")
	private final int level;

	@JsonProperty("e")
	private final int exp;

	public LevelComponentExMessage(int level, int exp) {

		this.level = level;
		this.exp = exp;
	}

	public int getLevel() {
		return level;
	}

	public int getExp() {
		return exp;
	}

	@Override
	public String toString() {
		return String.format("LevelComponentEx[lv: %d, exp: %d]", getLevel(), getExp());
	}
}
