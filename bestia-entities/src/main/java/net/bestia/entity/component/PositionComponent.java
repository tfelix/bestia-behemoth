package net.bestia.entity.component;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.bestia.model.domain.Direction;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;

/**
 * Entity with this component have a defined position in the world. Point refers
 * to the anchor point of a sprite if there is any.
 * 
 * @author Thomas Felix
 *
 */
@ComponentSync(SyncType.ALL)
@ComponentActor("net.bestia.zoneserver.actor.entity.component.MovementComponentActor")
public class PositionComponent extends Component {

	private static final long serialVersionUID = 1L;

	private CollisionShape shape = new Point();
	private Direction facing = Direction.SOUTH;
	private boolean sightBlocking = false;

	public PositionComponent(long id) {
		super(id);
		// no op.
	}

	@Override
	public void clear() {
		shape = new Point();
		facing = Direction.SOUTH;
		sightBlocking = false;
	}

	/**
	 * Returns the flag if the entity is blocking the line of sight with its
	 * collision shape.
	 * 
	 * @return TRUE if the entity should block line of sight. FALSE otherwise.
	 */
	@JsonProperty("sb")
	public boolean isSightBlocking() {
		return sightBlocking;
	}

	/**
	 * Sets the flag if the entity blocks the line of sight.
	 * 
	 * @param sightBlocking
	 *            The flag to set the sight blocking.
	 */
	public void setSightBlocking(boolean sightBlocking) {
		this.sightBlocking = sightBlocking;
	}

	/**
	 * Returns the anchor position of the entities {@link CollisionShape}.
	 * 
	 * @return Current position of the entity.
	 */
	@JsonProperty("p")
	public Point getPosition() {
		return shape.getAnchor();
	}

	/**
	 * Returns the current direction of facing. Important to check for AI for
	 * sight and detection checks.
	 * 
	 * @return The face direction.
	 */
	@JsonProperty("f")
	public Direction getFacing() {
		return facing;
	}

	/**
	 * Set the facing direction.
	 * 
	 * @param facing
	 *            The new facing.
	 */
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
		if (shape == null) {
			throw new IllegalStateException("Shape must be set first via setShape.");
		}
		shape = shape.moveByAnchor(x, y);
	}

	/**
	 * Its an alias for {@link #setPosition(long, long)}.
	 * 
	 * @param pos
	 *            The new position.
	 */
	public void setPosition(Point pos) {
		setPosition(pos.getX(), pos.getY());
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
	 * Sets a new {@link CollisionShape} for this entity unit.
	 * 
	 * @param shape
	 *            The new collision shape. Can not be null.
	 */
	public void setShape(CollisionShape shape) {
		this.shape = Objects.requireNonNull(shape);
	}

	@Override
	public String toString() {
		return String.format("PositionComponent[id: %d, pos: %s, shape: %s, facing: %s]", getId(),
				getPosition().toString(),
				shape.toString(),
				facing.toString());
	}

	@Override
	public int hashCode() {
		return Objects.hash(facing, shape, sightBlocking);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final PositionComponent other = (PositionComponent) obj;
		return Objects.equals(facing, other.facing)
				&& Objects.equals(shape, other.shape)
				&& Objects.equals(sightBlocking, other.sightBlocking);
	}

}
