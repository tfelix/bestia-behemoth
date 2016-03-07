package net.bestia.zoneserver.proxy;

import java.util.HashMap;
import java.util.Map;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.World;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Direction;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.zone.shape.CollisionShape;
import net.bestia.zoneserver.zone.shape.Vector2;

public abstract class BestiaEntityProxy {

	private class ECSLocation extends Location {

		private static final long serialVersionUID = 1L;

		@Override
		public int getX() {

			final CollisionShape pos = positionMapper.get(entity).position;
			return pos.getAnchor().x;
		}

		@Override
		public int getY() {

			final CollisionShape pos = positionMapper.get(entity).position;
			return pos.getAnchor().y;
		}

		@Override
		public void setX(int x) {

			final Position pos = positionMapper.get(entity);
			final Vector2 posAnchor = pos.position.getAnchor();
			final int y = posAnchor.getAnchor().x;
			pos.position = pos.position.moveByAnchor(x, y);
		}

		@Override
		public void setY(int y) {

			final Position pos = positionMapper.get(entity);
			final Vector2 posAnchor = pos.position.getAnchor();
			final int x = posAnchor.getAnchor().x;
			pos.position = pos.position.moveByAnchor(x, y);
		}

	}

	protected final Entity entity;

	private final ComponentMapper<Position> positionMapper;
	private final Location proxyLocation = new ECSLocation();

	private Map<Integer, Long> attackUsageTimer = new HashMap<>();
	private Direction facing;

	public BestiaEntityProxy(World world, Entity entity) {

		this.positionMapper = world.getMapper(Position.class);
		this.entity = entity;
		this.setFacing(Direction.SOUTH);
	}

	public abstract StatusPoints getStatusPoints();

	public Location getLocation() {
		return proxyLocation;
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
		return entity.getId();
	}
}