package net.bestia.zoneserver.entity.component;

import java.util.Objects;

import net.bestia.model.domain.Direction;
import net.bestia.model.geometry.CollisionShape;

/**
 * Initializes a {@link PositionComponent} with a position.
 * 
 * @author Thomas Felix
 *
 */
public class PositionComponentSetter extends ComponentSetter<PositionComponent> {

	private final CollisionShape position;
	private final Direction direction;

	public PositionComponentSetter(CollisionShape bbox) {
		this(bbox, Direction.SOUTH);
	}

	public PositionComponentSetter(CollisionShape bbox, Direction facing) {
		super(PositionComponent.class);

		this.position = Objects.requireNonNull(bbox);
		this.direction = Objects.requireNonNull(facing);
	}

	@Override
	protected void performSetting(PositionComponent comp) {
		comp.setShape(position);
		comp.setFacing(direction);
	}

}
