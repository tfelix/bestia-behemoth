package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.DelayedEntityProcessingSystem;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.ecs.component.MobSpawn;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.PositionDomainProxy;
import net.bestia.zoneserver.ecs.component.StatusPoints;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.entity.NpcBestiaEntityFactory;
import net.bestia.zoneserver.ecs.entity.NpcBestiaMapper;
import net.bestia.zoneserver.ecs.entity.NpcBestiaMapper.Builder;

/**
 * Converts spawn entities to real mob entities after the spawn delay.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class MobDelaySpawnSystem extends DelayedEntityProcessingSystem {

	private ComponentMapper<MobSpawn> spawnMapper;
	private NpcBestiaEntityFactory mobFactory;
	
	@Wire
	private CommandContext ctx;

	public MobDelaySpawnSystem() {
		super(Aspect.all(MobSpawn.class));
		// no op.
	}

	@Override
	protected void initialize() {
		super.initialize();

		NpcBestiaMapper.Builder builder = new Builder();
		builder.setGroupMapper(world.getMapper(MobGroup.class));
		builder.setNpcBestiaMapper(world.getMapper(NPCBestia.class));
		builder.setPositionMapper(world.getMapper(Position.class));
		builder.setPositionProxyMapper(world.getMapper(PositionDomainProxy.class));
		builder.setStatusMapper(world.getMapper(StatusPoints.class));
		builder.setVisibleMapper(world.getMapper(Visible.class));
		builder.setMovementMapper(world.getMapper(Movement.class));
		builder.setBestiaMapper(world.getMapper(Bestia.class));
		builder.setStatusMapper(world.getMapper(StatusPoints.class));
		final NpcBestiaMapper mapper = builder.build();
		
		// TODO Zone namen festlegen.
		this.mobFactory = new NpcBestiaEntityFactory("zonename?", world, mapper);
	}

	@Override
	protected float getRemainingDelay(Entity e) {
		return spawnMapper.get(e).delay;
	}

	@Override
	protected void processDelta(Entity e, float accumulatedDelta) {
		final MobSpawn spawn = spawnMapper.get(e);
		spawn.delay -= accumulatedDelta;
	}

	@Override
	protected void processExpired(Entity e) {
		final MobSpawn spawn = spawnMapper.get(e);
		
		mobFactory.create(spawn);
	}
}
