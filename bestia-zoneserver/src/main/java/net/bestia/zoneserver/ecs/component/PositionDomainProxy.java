package net.bestia.zoneserver.ecs.component;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.zone.shape.CollisionShape;

/**
 * This class wraps an domain location object. So each change to the position in
 * the ECS entity will be mirrored to the domain object as well.
 * 
 * @author Thomas
 *
 */
public class PositionDomainProxy extends Position {

	private Location domainPosition = null;

	/**
	 * Ctd. Ctor.
	 */
	public PositionDomainProxy() {

	}

	public void setDomainPosition(Location domainPosition) {
		this.domainPosition = domainPosition;
	}

	/**
	 * Helper method to set the anchor/position of the {@link CollisionShape} to
	 * the desired location.
	 * 
	 * @param x
	 *            New x coordinate.
	 * @param y
	 *            New y coordinate.
	 */
	public void setPosition(int x, int y) {
		super.setPosition(x, y);
		if (domainPosition != null) {
			domainPosition.setX(x);
			domainPosition.setY(y);
		}
	}

	public void setX(int x) {
		super.setX(x);
		if (domainPosition != null) {
			domainPosition.setX(x);
		}
	}

	public void setY(int y) {
		super.setY(y);
		if(domainPosition != null) {
			domainPosition.setY(y);
		}	
	}
}
