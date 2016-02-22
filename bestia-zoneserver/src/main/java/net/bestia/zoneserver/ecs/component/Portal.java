package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.zone.shape.CollisionShape;

public class Portal extends Component {

	public Location destination;
	public CollisionShape area;
	
}
