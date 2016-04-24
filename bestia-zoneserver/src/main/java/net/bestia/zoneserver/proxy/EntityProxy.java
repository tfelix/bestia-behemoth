package net.bestia.zoneserver.proxy;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.annotations.Wire;

import net.bestia.messages.entity.EntityDamageMessage;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Direction;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.misc.Damage;
import net.bestia.zoneserver.ecs.component.EntityComponent;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.ecs.manager.UuidManager;

@Wire
public class EntityProxy implements Entity {

	protected final int entityID;
	private final String uuid;

	protected final World world;
	protected ComponentMapper<Position> positionMapper;
	protected ComponentMapper<Visible> visibleMapper;
	protected ComponentMapper<EntityComponent> bestiaMapper;
	protected ComponentMapper<Movement> movementMapper;
	protected ComponentMapper<net.bestia.zoneserver.ecs.component.StatusPoints> statusMapper;
	
	private PlayerBestiaSpawnManager playerSpawnManager;

	private Direction facing;
	private final Location location;
	private final StatusPoints statusPoints = new StatusPoints();

	private final Map<Integer, Long> cooldownCache = new HashMap<>();

	/**
	 * The walkspeed is based upon status, equipments and status effects.
	 */
	private float walkspeed = 1.0f;

	public EntityProxy(World world, int entityID) {

		this.world = Objects.requireNonNull(world, "World must not be null.");

		// World inject does not work here dunno why.
		positionMapper = world.getMapper(Position.class);
		visibleMapper = world.getMapper(Visible.class);
		bestiaMapper = world.getMapper(EntityComponent.class);
		movementMapper = world.getMapper(Movement.class);
		statusMapper = world.getMapper(net.bestia.zoneserver.ecs.component.StatusPoints.class);
		
		playerSpawnManager = world.getSystem(PlayerBestiaSpawnManager.class);

		// Create the entity.
		this.entityID = entityID;
		this.uuid = world.getSystem(UuidManager.class).getUuid(world.getEntity(entityID)).toString();

		this.setFacing(Direction.SOUTH);

		// Create a placeholder location and proxy pos and location with the loc
		// proxy.
		location = positionMapper.get(entityID);

		bestiaMapper.get(entityID).manager = this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.zoneserver.proxy.Entity#getStatusPoints()
	 */
	@Override
	public StatusPoints getStatusPoints() {
		return statusPoints;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.zoneserver.proxy.Entity#getLocation()
	 */
	@Override
	public Location getLocation() {
		return location;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.zoneserver.proxy.Entity#getFacing()
	 */
	@Override
	public Direction getFacing() {
		return facing;
	}

	public void setFacing(Direction facing) {
		this.facing = facing;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.zoneserver.proxy.Entity#getEntityId()
	 */
	@Override
	public int getEntityId() {
		return entityID;
	}

	/**
	 * If something has changed on which walkspeed depends then call this method
	 * to re-evaluate the current walkspeed.
	 */
	protected void calculateWalkspeed() {
		final StatusPoints sp = getStatusPoints();
		final int spd = sp.getSpd();
		double ws = Math.sqrt(spd / 2.0) / 10;
		if (ws > 1.2) {
			ws = 1.2;
		}

		setWalkspeed((float) (1.0 + ws));
	}

	protected void setWalkspeed(float walkspeed) {
		this.walkspeed = walkspeed;
		final Movement m = movementMapper.getSafe(entityID);
		if (m != null) {
			m.setWalkspeed(walkspeed);
		}
	}

	/**
	 * Returns the current walkspeed based on the current stats, status effects
	 * and equipment. A walkspeed of 1.0 means normal speed. The walkspeed is
	 * currently capped between 0 and 3.
	 * 
	 * @return The current walkspeed between 0 and 3.
	 */
	public float getWalkspeed() {
		return walkspeed;
	}

	/**
	 * Returns the walkspeed normalized to an integer between 0 and 300.
	 * 
	 * @return The current walkspeed as an integer between 0 and 300.
	 */
	public int getWalkspeedInt() {
		final float speed = getWalkspeed();
		return (int) (100 * speed);
	}

	@Override
	public void takeDamage(Damage dmg) {
		
		dmg.setEntityUUID(uuid);
		
		// Broadcast the damage to the players in sight.
		EntityDamageMessage dmgMsg = new EntityDamageMessage(dmg);
		playerSpawnManager.sendMessageToSightrange(entityID, dmgMsg);
		
		// TODO ggf. damage reduction hier anwenden.
		
		if(!getStatusPoints().subtractHp(dmg.getDamage())) {
			// Entity was killed.
			getStatusPoints().setCurrentHp(1);
			getStatusPoints().setCurrentMana(0);
			kill();
		}
	}

	@Override
	public Collection<Attack> getAttacks() {
		return Collections.emptyList();
	}

	@Override
	public int getRemainingCooldown(int attackId) {
		if (cooldownCache.containsKey(attackId)) {
			final long curTime = System.currentTimeMillis();
			return (int) (cooldownCache.get(attackId) - curTime);
		} else {
			return -1;
		}
	}

	/**
	 * A simple entity can not use any attack. So this will always return false.
	 * 
	 * @return Always false.
	 */
	@Override
	public boolean useAttack(Attack atk) {
		return false;
	}

	@Override
	public void kill() {
		world.delete(entityID);
	}

	/**
	 * Triggers the cooldown for the given attack. It will keep an internal list
	 * of cooldowns.
	 */
	@Override
	public void triggerCooldown(Attack atk) {

		final long cd = System.currentTimeMillis() + atk.getCooldown();

		cooldownCache.put(atk.getId(), cd);
	}

	@Override
	public String toString() {
		return String.format("EntityProxy[entityId: %d, loc: %s, facing: %s]", entityID, getLocation().toString(),
				facing.toString());
	}

	@Override
	public Element getElement() {
		return Element.NORMAL;
	}

	@Override
	public int getLevel() {
		return 1;
	}

	@Override
	public String getUUID() {
		return uuid;
	}
}