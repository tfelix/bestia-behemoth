package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import net.bestia.zoneserver.proxy.EntityProxy;

/**
 * The entity contains a bestia component.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Bestia extends Component {

	public EntityProxy manager;

	/**
	 * Std. Ctor for artemis.
	 */
	public Bestia() {
		// no op.
	}

	public Bestia(EntityProxy manager) {
		this.manager = manager;
	}
}
