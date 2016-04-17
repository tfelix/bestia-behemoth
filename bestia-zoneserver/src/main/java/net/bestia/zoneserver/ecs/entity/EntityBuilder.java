package net.bestia.zoneserver.ecs.entity;

import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * <p>
 * The builder can be used to create a blue print for the factories to create an
 * entity.
 * </p>
 * <p>
 * Will be often used by the scripts to create and spawn entities on the fly.
 * Other uses might be possible though.
 * </p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityBuilder {

	private static final Logger LOG = LogManager.getLogger(EntityBuilder.class);
	
	public enum EntityType {
		MOB,
		BASIC,
		ITEM
	}

	private final EntityFactory factory;

	private Vector2 position;
	private EntityType type;

	public EntityBuilder() {
		factory = null;
		type = EntityType.BASIC;
	}

	public EntityBuilder(EntityType type, EntityFactory factory) {
		this.factory = factory;
		this.type = type;
	}

	public void spawn() {
		if (factory == null) {
			LOG.warn("Factory is not set. Can not build entity.");
			return;
		}
		if (factory.canSpawn(this)) {
			factory.spawn(this);
		}
	}
	
	public EntityBuilder setEntityType(EntityType type) {
		this.type = type;
		return this;
	}

	/**
	 * Sets a basic sprite for this entity.
	 * 
	 * @param name
	 *            Name of the sprite to use.
	 */
	public EntityBuilder setSprite(String name) {
		return this;
	}
	
	public EntityBuilder setPosition(int x, int y) {
		position = new Vector2(x, y);
		LOG.trace("Position was set: {}", position.toString());
		return this;
	}
	
	public EntityBuilder setHp(int hp) {
		LOG.trace("HP was set: {}", hp);
		
		return this;
	}
	
	public EntityBuilder setTickCallback(int interval, Callable<Void> fn) {
		
		return this;
	}

}
