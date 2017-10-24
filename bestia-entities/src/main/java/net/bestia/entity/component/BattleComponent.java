package net.bestia.entity.component;

/**
 * Contains data regarding a battle. This is important to distribute EXP for
 * example after a battle has taken place. It also keeps track which entity is
 * currently being attacked by this entity.
 * 
 * @author Thomas Felix
 *
 */
public class BattleComponent extends Component {

	private static final long serialVersionUID = 1L;
	private long lastHitTime;

	// private final Map<Long, Long>

	public BattleComponent(long id) {
		super(id);
		// no op
	}

}
