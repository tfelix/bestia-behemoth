package net.bestia.entity.component;

/**
 * Level components allow entities to receive exp and level up.
 * 
 * @author Thomas Felix
 *
 */
public class LevelComponent extends Component {

	public static final int MAX_LEVEL = 50;

	private static final long serialVersionUID = 1L;

	public LevelComponent(long id) {
		super(id);
		// no op.
	}

	private int level;
	private int exp;

	/**
	 * The level of the entity.
	 * 
	 * @return The level of the entity.
	 */
	public int getLevel() {
		return level;
	}

	public int getKilledExp() {
		return level * 10;
	}

	public void setLevel(int level) {

		if (level < 0 || level > MAX_LEVEL) {
			throw new IllegalArgumentException("Level can not be negative and bigger then" + MAX_LEVEL);
		}

		this.level = level;
	}

	public int getExp() {
		return exp;
	}

	public void setExp(int exp) {

		if (exp < 0) {
			throw new IllegalArgumentException("Exp must be positive.");
		}

		this.exp = exp;
	}

	@Override
	public String toString() {
		return String.format("LevelComponent[id: %d, level: %d, exp: %d]", getId(), getLevel(), getExp());
	}
}
