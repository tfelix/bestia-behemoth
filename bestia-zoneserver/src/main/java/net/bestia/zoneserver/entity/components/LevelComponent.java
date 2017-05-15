package net.bestia.zoneserver.entity.components;

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
	
	public int getKilledExp() {
		return level * 10;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public int getExp() {
		return exp;
	}

	public void setExp(int exp) {
		this.exp = exp;
	}
}
