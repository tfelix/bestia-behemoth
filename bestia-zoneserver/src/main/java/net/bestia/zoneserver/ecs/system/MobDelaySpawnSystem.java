package net.bestia.zoneserver.ecs.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.DelayedEntityProcessingSystem;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.MobSpawn;
import net.bestia.zoneserver.ecs.entity.EcsEntityFactory;
import net.bestia.zoneserver.ecs.entity.EntityBuilder;
import net.bestia.zoneserver.ecs.entity.EntityBuilder.EntityType;
import net.bestia.zoneserver.ecs.entity.EntityFactory;

/**
 * Converts spawn entities to real mob entities after the spawn delay.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class MobDelaySpawnSystem extends DelayedEntityProcessingSystem {

	private ComponentMapper<MobSpawn> spawnMapper;
	private EntityFactory entityFactory;
	
	@Wire
	private CommandContext ctx;

	public MobDelaySpawnSystem() {
		super(Aspect.all(MobSpawn.class));
		// no op.
	}
	
	@Override
	protected void initialize() {
		super.initialize();
		
		entityFactory = new EcsEntityFactory(getWorld());
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

		final EntityBuilder eb = new EntityBuilder();
		
		eb.setSprite(spawn.mob.getImage());
		eb.setPosition(spawn.coordinates);
		eb.setEntityType(EntityType.MOB);
		
		entityFactory.spawn(eb);
		
		e.deleteFromWorld();
	}
}
