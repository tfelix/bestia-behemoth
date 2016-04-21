package net.bestia.zoneserver.proxy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.annotations.Wire;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Direction;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;

@Wire
public abstract class BestiaEntityProxy {

	protected final int entityID;

	protected ComponentMapper<Position> positionMapper;
	protected ComponentMapper<Visible> visibleMapper;
	protected ComponentMapper<Bestia> bestiaMapper;
	protected ComponentMapper<Movement> movementMapper;
	protected ComponentMapper<net.bestia.zoneserver.ecs.component.StatusPoints> statusMapper;

	private Map<Integer, Long> attackUsageTimer = new HashMap<>();
	private Direction facing;
	private final Location location;

	/**
	 * The walkspeed is based upon status, equipments and status effects.
	 */
	private float walkspeed = 1.0f;

	public BestiaEntityProxy(World world, int entityID) {
		Objects.requireNonNull(world, "World must not be null.");
		
		positionMapper = world.getMapper(Position.class);
		visibleMapper = world.getMapper(Visible.class);
		bestiaMapper = world.getMapper(Bestia.class);
		movementMapper = world.getMapper(Movement.class);
		statusMapper = world.getMapper(net.bestia.zoneserver.ecs.component.StatusPoints.class);

		// Create the entity.
		this.entityID = entityID;

		this.setFacing(Direction.SOUTH);

		// Create a placeholder location and proxy pos and location with the loc
		// proxy.
		location = positionMapper.get(entityID);
		
		bestiaMapper.get(entityID).manager = this;
	}

	public abstract StatusPoints getStatusPoints();

	public Location getLocation() {
		return location;
	}

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
}