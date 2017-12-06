package net.bestia.entity.component;

import java.util.Objects;

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

		if (level <= 0) {
			level = 1;
		}

		this.level = level;
	}

	public int getExp() {
		return exp;
	}

	void setExp(int exp) {

		if (exp < 0) {
			exp = 0;
		}

		this.exp = exp;
	}

	@Override
	public int hashCode() {
		return Objects.hash(level, exp);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof LevelComponent)) {
			return false;
		}
		final LevelComponent other = (LevelComponent) obj;

		return Objects.equals(level, other.level)
				&& Objects.equals(exp, other.exp);
	}

	@Override
	public String toString() {
		return String.format("LevelComponent[id: %d, level: %d, exp: %d]", getId(), getLevel(), getExp());
	}
}
