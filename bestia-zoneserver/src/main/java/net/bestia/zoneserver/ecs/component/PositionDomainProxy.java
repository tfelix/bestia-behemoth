package net.bestia.zoneserver.ecs.component;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.zone.shape.CollisionShape;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * This class wraps an domain location object. So each change to the position in
 * the ECS entity will be mirrored to the domain object as well.
 * 
 * @author Thomas
 *
 */
public class PositionDomainProxy extends Position {

	private CollisionShape position;
	private Location domainPosition;

	/**
	 * Ctd. Ctor.
	 */
	public PositionDomainProxy() {
		position = new Vector2(0, 0);
		domainPosition = new Location();
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
		position = position.moveByAnchor(x, y);
		domainPosition.setX(x);
		domainPosition.setY(y);
	}

	public void setX(int x) {
		position = position.moveByAnchor(x, position.getAnchor().y);
		domainPosition.setX(x);
	}
	
	public void setY(int y) {
		position = position.moveByAnchor(position.getAnchor().x, y);
		domainPosition.setY(y);
	}
}
