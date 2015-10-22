package net.bestia.zoneserver.ecs.component;

import net.bestia.zoneserver.manager.BestiaManager;

import com.artemis.Component;

/**
 * The entity contains a bestia component.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Bestia extends Component {

	public BestiaManager bestiaManager;

	/**
	 * Std. Ctor for artemis.
	 */
	public Bestia() {
		// no op.
	}

	public Bestia(BestiaManager manager) {
		this.bestiaManager = manager;
	}
}
