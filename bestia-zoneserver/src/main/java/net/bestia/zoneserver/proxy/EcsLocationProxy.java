package net.bestia.zoneserver.proxy;

import java.io.Serializable;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.ecs.component.Position;

/**
 * This class wraps an domain {@link Location} as well as a {@link Position}
 * component. Calling the methods will keep both location objects in sync.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EcsLocationProxy implements Location, Serializable {

	private static final long serialVersionUID = 1L;

	private final Position ecsPosition;
	private final Location location;

	public EcsLocationProxy(Position ecsPosition, Location location) {
		if (ecsPosition == null) {
			throw new IllegalArgumentException("ecsPosition can not be null.");
		}
		if (location == null) {
			throw new IllegalArgumentException("Location can not be null.");
		}

		this.ecsPosition = ecsPosition;
		this.location = location;
	}

	@Override
	public int getX() {

		return ecsPosition.getPosition().getAnchor().x;

	}

	@Override
	public int getY() {

		return ecsPosition.getPosition().getAnchor().y;

	}

	@Override
	public void setX(int x) {

		location.setX(x);
		ecsPosition.setX(x);

	}

	@Override
	public void setY(int y) {

		location.setY(y);
		ecsPosition.setY(y);

	}

	@Override
	public String getMapDbName() {
		return location.getMapDbName();
	}

	@Override
	public void setMapDbName(String mapDbName) {
		location.setMapDbName(mapDbName);
	}

}
