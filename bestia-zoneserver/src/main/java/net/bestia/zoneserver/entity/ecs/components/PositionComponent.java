package net.bestia.zoneserver.entity.ecs.components;

import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;

/**
 * Holds the current position of an entity.
 * 
 * @author Thomas Felix
 *
 */
public class PositionComponent extends Component {

	public PositionComponent(long id) {
		super(id);
		// no op.
	}

	private CollisionShape shape;

	public Point getPosition() {
		return shape.getAnchor();
	}

	public CollisionShape getShape() {
		return shape;
	}

	/**
	 * Sets the position of this component to the given coordiantes.
	 * 
	 * @param x
	 * @param y
	 */
	public void setPosition(long x, long y) {
		shape = shape.moveByAnchor(x, y);
	}

}
