package net.bestia.zoneserver.proxy;

import java.util.HashMap;
import java.util.Map;

import com.artemis.Archetype;
import com.artemis.ComponentMapper;
import com.artemis.World;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Direction;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.LocationDomain;
import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.PositionDomainProxy;
import net.bestia.zoneserver.zone.shape.Vector2;

public abstract class BestiaEntityProxy {

	protected final int entityID;
	protected final World world;
	
	private final ComponentMapper<PositionDomainProxy> positionProxyMapper;
	private final ComponentMapper<Position> positionMapper;

	private Map<Integer, Long> attackUsageTimer = new HashMap<>();
	private Direction facing;
	private final Location location;

	public BestiaEntityProxy(World world, Vector2 position) {

		this.world = world;
		
		// Create the entity.
		entityID = world.create(getArchetype());

		this.positionProxyMapper = world.getMapper(PositionDomainProxy.class);
		this.positionMapper = world.getMapper(Position.class);
		
		this.setFacing(Direction.SOUTH);

		final Position pos = positionMapper.get(entityID);
		final PositionDomainProxy posProxy = positionProxyMapper.get(entityID);
		// Create a placeholder location and proxy pos and location with the loc
		// proxy.	
		final Location domLocation = new LocationDomain();
		location = new EcsLocationProxy(pos, domLocation);

		// Set the ESC proxy.
		posProxy.setDomainPosition(location);
		location.setMapDbName("");
		location.setX(position.x);
		location.setY(position.y);
	}

	public abstract StatusPoints getStatusPoints();

	public Location getLocation() {
		return location;
	}

	protected abstract Archetype getArchetype();

	public abstract int getLevel();

	/**
	 * Calculates the current HP regeneration rate based on stats and per tick.
	 * 
	 * @return The current HP regeneration rate per tick.
	 */
	public float getHpRegenerationRate() {
		final StatusPoints statusPoints = getStatusPoints();
		final int level = getLevel();
		final float regen = (statusPoints.getDef() * 4 + statusPoints.getSpDef() * 1.5f + level) / 100.0f;
		return regen;
	}

	/**
	 * Calculates the current Mana regeneration rate based on stats and per
	 * tick.
	 * 
	 * @return The current Mana regeneration rate per tick.
	 */
	public float getManaRegenerationRate() {
		final StatusPoints statusPoints = getStatusPoints();
		final int level = getLevel();
		final float regen = (statusPoints.getDef() * 1.5f + statusPoints.getSpDef() * 3 + level) / 100.0f;
		return regen;
	}

	/**
	 * Tries to use this attack. The usage of the attack will fail if there is
	 * not enough mana to use it or if the cooldown for this attack is still
	 * ticking. If the atk is null it wont execute and return false.
	 * <p>
	 * Note: There are NO checks if the bestia actually owns this attack. This
	 * is especially for NPC bestias so they can use all attacks they like via
	 * scripts for example.
	 * </p>
	 * 
	 * @param atk
	 *            Attack to be used.
	 */
	public boolean useAttack(Attack atk) {

		if (atk == null) {
			return false;
		}

		final StatusPoints sp = getStatusPoints();

		// Check available mana.
		if (sp.getCurrentMana() < atk.getManaCost()) {
			return false;
		}

		final long curTime = System.currentTimeMillis();
		final long attackCooldownTime;
		if (attackUsageTimer.containsKey(atk.getId())) {
			attackCooldownTime = attackUsageTimer.get(atk.getId()) + atk.getCooldown();
		} else {
			attackCooldownTime = 0;
		}

		if (curTime < attackCooldownTime) {
			return false;
		}

		// Use the attack.
		sp.setCurrentMana(sp.getCurrentMana() - atk.getManaCost());
		attackUsageTimer.put(atk.getId(), curTime);

		return true;
	}

	public Direction getFacing() {
		return facing;
	}

	public void setFacing(Direction facing) {
		this.facing = facing;
	}

	public int getEntityId() {
		return entityID;
	}
}