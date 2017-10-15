package net.bestia.entity.component;

/**
 * Level components allow entities to receive exp and level up.
 * 
 * @author Thomas Felix
 *
 */
public class LevelComponent extends Component {

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

	void setLevel(int level) {

		if (level < 0) {
			throw new IllegalArgumentException("Level can not be negative." );
		}

		this.level = level;
	}

	public int getExp() {
		return exp;
	}

	void setExp(int exp) {

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
