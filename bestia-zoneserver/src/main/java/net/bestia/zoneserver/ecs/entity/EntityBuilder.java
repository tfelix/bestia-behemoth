package net.bestia.zoneserver.ecs.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import groovy.lang.Closure;
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
		MOB, BASIC, ITEM
	}

	private final EntityFactory factory;

	Vector2 position;
	EntityType type;
	String sprite;
	String mobName;
	String mobGroup;
	int tickDelay;
	int hp;
	Closure<Void> tickCallback;

	public EntityBuilder() {
		factory = null;
		type = EntityType.BASIC;
	}

	public EntityBuilder(EntityType type) {
		this.factory = null;
		this.type = type;
	}

	public EntityBuilder(EntityType type, EntityFactory factory) {
		this.factory = factory;
		this.type = type;
	}

	/**
	 * Resets the builder into a clean state.
	 */
	public void clear() {
		position = null;
		type = null;
		sprite = null;
		mobName = null;
		tickCallback = null;
		tickDelay = 0;
		mobGroup = null;
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
	 * Sets the name of the mob to be spawned.
	 * 
	 * @param mobName
	 */
	public EntityBuilder setMobName(String mobName) {
		this.mobName = mobName;
		return this;
	}
	
	public EntityBuilder setMobGroup(String mobGroup) {
		this.mobGroup = mobGroup;
		return this;
	}

	/**
	 * Sets a basic sprite for this entity.
	 * 
	 * @param name
	 *            Name of the sprite to use.
	 */
	public EntityBuilder setSprite(String name) {
		this.sprite = name;
		return this;
	}

	public EntityBuilder setPosition(int x, int y) {
		position = new Vector2(x, y);
		LOG.trace("Position was set: {}", position.toString());
		return this;
	}

	public EntityBuilder setPosition(Vector2 pos) {
		position = pos;
		LOG.trace("Position was set: {}", position.toString());
		return this;
	}

	/**
	 * Sets the HP if an entity is build from scratch. This is not used if an
	 * mob or an item is spawned.
	 * 
	 * @param hp
	 * @return
	 */
	public EntityBuilder setHp(int hp) {
		if(hp < 0) {
			hp = 0;
		}
		LOG.trace("HP was set: {}", hp);
		this.hp = hp;
		return this;
	}

	public EntityBuilder setTickCallback(int interval, Closure<Void> fn) {
		this.tickDelay = interval;
		this.tickCallback = fn;
		return this;
	}

}
