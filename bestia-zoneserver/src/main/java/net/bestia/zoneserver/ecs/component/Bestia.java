package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import net.bestia.zoneserver.proxy.BestiaEntityProxy;

/**
 * The entity contains a bestia component.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Bestia extends Component {

	public BestiaEntityProxy manager;

	/**
	 * Std. Ctor for artemis.
	 */
	public Bestia() {
		// no op.
	}

	public Bestia(BestiaEntityProxy manager) {
		this.manager = manager;
	}
}
