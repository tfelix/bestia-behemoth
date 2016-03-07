package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.DelayedEntityProcessingSystem;

import net.bestia.zoneserver.ecs.component.MobSpawn;
import net.bestia.zoneserver.ecs.entity.MobEntityFactory;

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

	//private final static Logger LOG = LogManager.getLogger(MobSpawnSystem.class);

	private ComponentMapper<MobSpawn> spawnMapper;

	private MobEntityFactory mobFactory;

	public MobSpawnSystem() {
		super(Aspect.all(MobSpawn.class));
		// no op.
	}

	@Override
	protected void initialize() {
		super.initialize();

		mobFactory = new MobEntityFactory(world);
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
		mobFactory.create(spawn.mob, spawn.getGroup(), spawn.coordinates);
	}
}
