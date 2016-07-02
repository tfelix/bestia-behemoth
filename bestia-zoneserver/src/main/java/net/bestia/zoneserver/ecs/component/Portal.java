package net.bestia.zoneserver.ecs.component;

import java.io.Serializable;

import com.artemis.Component;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.zone.shape.CollisionShape;

public class Portal extends Component implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public Location destination;
	public CollisionShape area;
	
}
