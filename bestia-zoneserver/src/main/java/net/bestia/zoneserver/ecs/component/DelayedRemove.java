package net.bestia.zoneserver.ecs.component;

import java.io.Serializable;

import com.artemis.Component;

/**
 * Each entity with such an component will be removed after the delay has
 * expired. Delay is in ms.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class DelayedRemove extends Component implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public int removeDelay;

	public DelayedRemove() {

	}

	public DelayedRemove(int delay) {
		this.removeDelay = delay;
	}
}
