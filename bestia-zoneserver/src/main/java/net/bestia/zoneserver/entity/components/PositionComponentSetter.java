package net.bestia.zoneserver.entity.components;

import java.util.Objects;

import net.bestia.model.domain.Direction;
import net.bestia.model.geometry.Point;

public class PositionComponentSetter extends ComponentSetter<PositionComponent> {

	private final Point position;
	private final Direction direction;

	public PositionComponentSetter(Point position) {
		this(position, Direction.SOUTH);
	}
	
	public PositionComponentSetter(Point position, Direction facing) {
		super(PositionComponent.class);

		this.position = Objects.requireNonNull(position);
		this.direction = Objects.requireNonNull(facing);
	}

	@Override
	public void setComponent(PositionComponent comp) {
		comp.setPosition(position);
		comp.setFacing(direction);
	}

}
