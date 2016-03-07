package net.bestia.zoneserver.ecs.entity;

import com.artemis.World;

public abstract class EntityFactory {

	protected final World world;

	public EntityFactory(World world) {
		if (world == null) {
			throw new IllegalArgumentException("World can not be null.");
		}

		this.world = world;
	}

}
