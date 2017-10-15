package net.bestia.entity.component;

import java.util.Map;
import java.util.Set;

/**
 * Contains data regarding a battle. This is important to distribute EXP for
 * example after a battle has taken place.
 * 
 * @author Thomas Felix
 *
 */
public class BattleComponent extends Component {
	
	private long lastHitTime;
	
	//private final Map<Long, Long> 

	public BattleComponent(long id) {
		super(id);
		// no op
	}

	
}
