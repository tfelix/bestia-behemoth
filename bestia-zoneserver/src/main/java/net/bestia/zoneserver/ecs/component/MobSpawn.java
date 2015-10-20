package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import net.bestia.model.domain.Bestia;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Spawns the given mob after the given delay has passed on the defined
 * coordiantes.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MobSpawn extends Component {

	public int delay;
	public Bestia mob;
	public Vector2 coordinates;

	public MobSpawn() {
		// no op.
	}

	public MobSpawn(int delay, Bestia mob, Vector2 coordinates) {
		this.delay = delay;
		this.mob = mob;
		this.coordinates = coordinates;
	}
}
