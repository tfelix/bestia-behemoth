package net.bestia.zoneserver.ecs.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.annotations.Wire;

import net.bestia.model.domain.Bestia;
import net.bestia.zoneserver.ecs.component.AI;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.ecs.component.MobSpawn;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.StatusPoints;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.proxy.NpcEntityProxy;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Factory for the creation of {@link NpcEntityProxy}s.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
class NpcBestiaEntityFactory extends EntityFactory {

	private static final Logger LOG = LogManager.getLogger(NpcBestiaEntityFactory.class);

	private final Archetype npcBestiaArchetype;
	
	@Wire
	private ComponentMapper<MobGroup> mobGroupMapper;

	public NpcBestiaEntityFactory( World world) {
		super(world);
		
		npcBestiaArchetype = new ArchetypeBuilder()
				.add(Position.class)
				.add(MobGroup.class)
				.add(Attacks.class)
				.add(net.bestia.zoneserver.ecs.component.Bestia.class)
				.add(NPCBestia.class)
				.add(StatusPoints.class)
				.add(Visible.class)
				.add(AI.class)
				.build(world);
		
		world.inject(this);
	}

	public NpcEntityProxy create(MobSpawn mobSpawn) {

		final Bestia spawnBestia = mobSpawn.mob;
		final String group = mobSpawn.getGroup();
		final Vector2 pos = mobSpawn.coordinates;

		return create(spawnBestia, pos, group);
	}

	public NpcEntityProxy create(Bestia bestia, Vector2 position) {

		return create(bestia, position, "none");
	}

	/**
	 * Creates an {@link NpcEntityProxy} and spawns it directly to the
	 * given position in the responsible zone.
	 * 
	 * @param bestia
	 * @param position
	 * @param groupName
	 * @return
	 */
	public NpcEntityProxy create(Bestia bestia, Vector2 position, String groupName) {

		final int entityID = world.create(npcBestiaArchetype);

		final NpcEntityProxy mobBestia = new NpcEntityProxy(world, entityID, bestia);
		mobBestia.getLocation().setPos(position.x, position.y);
		mobGroupMapper.get(entityID).groupName = groupName;

		LOG.debug("Spawned mob: {}", mobBestia.toString());

		return mobBestia;
	}

	@Override
	public void spawn(EntityBuilder builder) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean canSpawn(EntityBuilder builder) {
		// TODO Ersetzen.
		return false;
	}
}
