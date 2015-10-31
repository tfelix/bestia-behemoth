package net.bestia.zoneserver.manager;

import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.World;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Test Proxy for the ECS.
 * 
 * @author Thomas
 *
 */
public class PlayerBestiaManagerECS implements PlayerBestiaManagerInterface {

	private final ComponentMapper<Attacks> attacksMapper;
	private final ComponentMapper<Position> positionMapper;
	private final ComponentMapper<net.bestia.zoneserver.ecs.component.PlayerBestia> playerBestiaMapper;

	private final World world;
	private final Entity entity;

	public PlayerBestiaManagerECS(World world, Entity entity) {
		this.world = world;
		this.entity = entity;
		this.attacksMapper = world.getMapper(Attacks.class);
		this.positionMapper = world.getMapper(Position.class);
		this.playerBestiaMapper = world.getMapper(net.bestia.zoneserver.ecs.component.PlayerBestia.class);

		if (!playerBestiaMapper.has(entity)) {
			throw new IllegalArgumentException("Entity is no player bestia entity! (Missing component).");
		}
	}

	@Override
	public void addExp(int exp) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getMaxItemWeight() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void updateStatusValues() {
		// TODO Auto-generated method stub

	}

	@Override
	public PlayerBestia getPlayerBestia() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getPlayerBestiaId() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getAccountId() {
		return playerBestiaMapper.get(entity).playerBestiaManager.getAccountId();
	}

	@Override
	public StatusPoints getStatusPoints() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Location getLocation() {
		return playerBestiaMapper.get(entity).playerBestiaManager.getLocation();
	}

	@Override
	public int getLevel() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setAttack(int slot, Attack atk) {
		final Attacks atkComp = attacksMapper.get(entity);
		
		atkComp.clear();
		
	}

	@Override
	public boolean useAttackInSlot(int slot) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Attack getAttackInSlot(int slot) {
		// TODO Auto-generated method stub
		return null;
	}

}
