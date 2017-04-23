package net.bestia.zoneserver.entity.components;

import java.util.Objects;

import net.bestia.model.domain.Direction;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;

public class PositionComponentSetter extends ComponentSetter<PositionComponent> {

	private final CollisionShape position;
	private final Direction direction;

	public PositionComponentSetter(Point position) {
		this(position, Direction.SOUTH);
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
