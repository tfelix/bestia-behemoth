package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.zone.shape.CollisionShape;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Represents the current position of the bestia on the map. We use a
 * {@link CollisionShape} to directly be able to check for collisions and script
 * trigger events issued from the bestia position.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Position extends Component implements Location {

	private CollisionShape shape;
	private Location location;

	/**
	 * Ctd. Ctor.
	 */
	public Position() {
		shape = new Vector2(0, 0);
		location = null;
	}

	@Override
	public String toString() {
		return String.format("Position[shape: %s]", shape.toString());
	}

	public CollisionShape getShape() {
		return shape;
	}

	@Override
	public String getMapDbName() {
		if (location != null) {
			return location.getMapDbName();
		}
		return "";
	}

	@Override
	public void setMapDbName(String mapDbName) {
		if (location != null) {
			location.setMapDbName(mapDbName);
		}
	}

	@Override
	public int getX() {
		return shape.getAnchor().x;
	}

	@Override
	public int getY() {
		return shape.getAnchor().y;
	}

	@Override
	public void setPos(int x, int y) {
		shape.moveByAnchor(x, y);
		if (location != null) {
			location.setPos(x, y);
		}
	}

	@Override
	public void set(Location loc) {
		shape.moveByAnchor(loc.getX(), loc.getY());
		if (location != null) {
			location.set(loc);
		}
	}

	@Override
	public void setX(int x) {
		final int curY = getY();
		shape.moveByAnchor(x, curY);
		if (location != null) {
			location.setX(x);
		}
	}

	@Override
	public void setY(int y) {
		final int curY = getX();
		shape.moveByAnchor(curY, y);
		if (location != null) {
			location.setY(y);
		}
	}

	public void setShape(CollisionShape shape) {
		if (shape == null) {
			throw new IllegalArgumentException("Shape can not be null.");
		}
		this.shape = shape;
		if (location != null) {
			location.setPos(shape.getAnchor().x, shape.getAnchor().y);
		}
	}
}
