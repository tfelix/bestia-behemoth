package net.bestia.zoneserver.entity.components;

import java.util.Objects;

import net.bestia.model.domain.Direction;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;

/**
 * Entity with this component have a defined position in the world. Point refers
 * to the anchor point of a sprite if there is any.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PositionComponent extends Component {

	private static final long serialVersionUID = 1L;

	public PositionComponent(long id) {
		super(id);
		// no op.
	}

	private CollisionShape shape;
	private Direction facing = Direction.SOUTH;

	public Point getPosition() {
		return shape.getAnchor();
	}
	
	public Direction getFacing() {
		return facing;
	}
	
	public void setFacing(Direction facing) {
		this.facing = facing;
	}

	/**
	 * Sets the position of this component to the given coordiantes.
	 * 
	 * @param x
	 *            New x position.
	 * @param y
	 *            New y position.
	 */
	public void setPosition(long x, long y) {
		shape = shape.moveByAnchor(x, y);
	}

	/**
	 * Returns the space unit which is used by this entity and can be used to
	 * perform collision checks.
	 * 
	 * @return The {@link CollisionShape} of this unit.
	 */
	public CollisionShape getShape() {
		return shape;
	}

	/**
	 * Returns the current movement speed. 1 is the nominal speed which is 1.4
	 * m/s (or 1.4 tiles per second). The minimum speed returned via this method
	 * is 0.01 otherwise it is clamped to 0 (which means the unit can no longer
	 * move). The upper limit is 10.
	 * 
	 * @return The current movement speed of the unit.
	 */
	// public float getMovementSpeed();

	/**
	 * Sets a new {@link CollisionShape} for this entity unit.
	 * 
	 * @param shape
	 *            The new collision shape. Can not be null.
	 */
	void setShape(CollisionShape shape) {
		this.shape = Objects.requireNonNull(shape);
	}

	/**
	 * Moves the entity along a certain path. This is important to be called
	 * from a script context. The can not be any holes inside this path. All
	 * path following elements must have only a distance of 1 from each other.
	 * 
	 * @param path
	 *            The path to follow.
	 */
	//void moveTo(List<Point> path);

}
