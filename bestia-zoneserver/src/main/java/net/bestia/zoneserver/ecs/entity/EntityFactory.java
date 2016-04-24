package net.bestia.zoneserver.ecs.entity;

import java.util.Objects;

import com.artemis.World;

import net.bestia.zoneserver.command.CommandContext;

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
	protected final CommandContext ctx;

	public EntityFactory(World world, CommandContext ctx) {
		this.world = Objects.requireNonNull(world, "World can not be null.");
		this.ctx = Objects.requireNonNull(ctx, "Context can not be null.");
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

	/**
	 * Checks if the string is null or empty. If this is the case an
	 * {@link IllegalArgumentException} will be thrown. Just a helper method to
	 * check the strings of the builders.
	 * 
	 * @param str
	 */
	protected void requireNonNullEmpty(String str) {
		if (str == null || str.isEmpty()) {
			throw new IllegalArgumentException("String is null or empty.");
		}
	}
}
