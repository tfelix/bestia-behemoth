package net.bestia.zoneserver.ecs.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.DelayedEntityProcessingSystem;

import net.bestia.zoneserver.ecs.component.AI;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.ecs.component.MobSpawn;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.manager.NPCBestiaManager;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Converts spawn entities to real mob entities after the spawn delay.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class MobSpawnSystem extends DelayedEntityProcessingSystem {

	private final static Logger LOG = LogManager.getLogger(MobSpawnSystem.class);

	private ComponentMapper<MobSpawn> spawnMapper;

	private ComponentMapper<MobGroup> groupMapper;
	// private ComponentMapper<AI> aiMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<Position> positionMapper;
	private ComponentMapper<Visible> visibleMapper;
	private ComponentMapper<NPCBestia> npcBestiaMapper;

	private Archetype npcBestiaArchtype;

	public MobSpawnSystem() {
		super(Aspect.all(MobSpawn.class));
		// no op.
	}

	@Override
	protected void initialize() {
		super.initialize();

		npcBestiaArchtype = new ArchetypeBuilder()
				.add(Visible.class)
				.add(AI.class)
				.add(Bestia.class)
				.add(NPCBestia.class)
				.add(MobGroup.class)
				.add(Position.class)
				.build(world);
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

		final int mob = world.create(npcBestiaArchtype);
		final Entity mobEntity = world.getEntity(mob);

		groupMapper.get(mob).groupName = spawn.getGroup();
		
		final Position pos = positionMapper.get(mob);
		pos.position = new Vector2(spawn.coordinates.x, spawn.coordinates.y);

		final NPCBestiaManager npcManager =  new NPCBestiaManager(spawn.mob, world, mobEntity);
		bestiaMapper.get(mob).bestiaManager = npcManager;
		npcBestiaMapper.get(mob).manager = npcManager;
		

		// Set the sprite name.
		visibleMapper.get(mob).sprite = spawn.mob.getDatabaseName();

		LOG.trace("Spawned mob: {}, entity id: {}", spawn.mob.getDatabaseName(), mob);
	}

}
