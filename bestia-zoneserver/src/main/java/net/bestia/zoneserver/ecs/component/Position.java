package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

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
public class Position extends Component {

	private CollisionShape position;

	/**
	 * Ctd. Ctor.
	 */
	public Position() {
		position = new Vector2(0, 0);
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
	}

	public void setX(int x) {
		final int y = position.getAnchor().y;
		setPosition(x, y);
	}

	public void setY(int y) {
		final int x = position.getAnchor().x;
		setPosition(x, y);
	}

	public CollisionShape getPosition() {
		return position;
	}

	public void setPosition(CollisionShape shape) {
		this.position = shape;
	}

	@Override
	public String toString() {
		return String.format("Position[shape: %s]", position.toString());
	}
}
