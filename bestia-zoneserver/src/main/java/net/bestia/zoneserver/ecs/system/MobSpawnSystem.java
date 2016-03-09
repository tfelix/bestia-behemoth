package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.DelayedEntityProcessingSystem;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.MobSpawn;
import net.bestia.zoneserver.proxy.NpcBestiaEntityFactory;
import net.bestia.zoneserver.proxy.NpcBestiaMapper;
import net.bestia.zoneserver.proxy.NpcBestiaMapper.Builder;

/**
 * Converts spawn entities to real mob entities after the spawn delay. It also
 * keeps track of the number of the spawned mob entities. If the number
 * decreases it will create
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class MobSpawnSystem extends DelayedEntityProcessingSystem {

	private ComponentMapper<MobSpawn> spawnMapper;
	private NpcBestiaEntityFactory mobFactory;
	
	@Wire
	private CommandContext ctx;

	private ComponentMapper<Bestia> bestiaMapper;

	public MobSpawnSystem() {
		super(Aspect.all(MobSpawn.class));
		// no op.
	}

	@Override
	protected void initialize() {
		super.initialize();

		NpcBestiaMapper.Builder builder = new Builder();

		builder.setBestiaMapper(bestiaMapper);

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
