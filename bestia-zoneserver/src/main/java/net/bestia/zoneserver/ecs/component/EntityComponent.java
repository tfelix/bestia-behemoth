package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import net.bestia.zoneserver.proxy.EntityProxy;

/**
 * The entity contains a {@link EntityProxy} component.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityComponent extends Component {

	public EntityProxy manager;

	/**
	 * Std. Ctor for artemis.
	 */
	public EntityComponent() {
		// no op.
	}

	public EntityComponent(EntityProxy manager) {
		this.manager = manager;
	}
}
