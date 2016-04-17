package net.bestia.zoneserver.ecs.entity;

import java.util.Objects;

import com.artemis.World;

/**
 * Simple base class for all entity factories. Maybe it is possible in the
 * future to combine all these entity factories into a single one which will
 * just take a description and redirect the creation of the entity to its
 * childs.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class EntityFactory {

	protected final World world;

	public EntityFactory(World world) {
		this.world = Objects.requireNonNull(world, "World can not be null.");
	}

	/**
	 * Spawns the entity described by this builder.
	 * 
	 * @param builder
	 */
	public abstract void spawn(EntityBuilder builder);

	/**
	 * Checks if the factory can handle and spawn an entity described by this
	 * builder object.
	 * 
	 * @param builder
	 *            The {@link EntityBuilder} describing this entity.
	 * @return TRUE if the factory supports this builder. FALSE otherwise.
	 */
	public abstract boolean canSpawn(EntityBuilder builder);

}
