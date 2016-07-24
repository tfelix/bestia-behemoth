package net.bestia.zoneserver.ecs.component;

import java.io.Serializable;

import com.artemis.Component;

import net.bestia.model.domain.Bestia;
import net.bestia.zoneserver.zone.shape.Point;

/**
 * Spawns the given mob after the given delay has passed on the defined
 * coordiantes.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MobSpawn extends Component implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public int delay;
	public Bestia mob;
	public Point coordinates;

	public MobSpawn() {
		// no op.
	}

	public MobSpawn(int delay, Bestia mob, Point coordinates) {
		this.delay = delay;
		this.mob = mob;
		this.coordinates = coordinates;
	}

	/**
	 * Returns the group name of this particular mob.
	 * 
	 * @return
	 */
	public String getGroup() {
		return String.format("group-%s", mob.getDatabaseName());
	}
}
