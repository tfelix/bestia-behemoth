package net.bestia.zoneserver.ecs.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.World;

import net.bestia.model.domain.Bestia;
import net.bestia.zoneserver.ecs.component.AI;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.ecs.component.MobSpawn;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.PositionDomainProxy;
import net.bestia.zoneserver.ecs.component.StatusPoints;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.proxy.NpcBestiaEntityProxy;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Factory for the creation of {@link NpcBestiaEntityProxy}s.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class NpcBestiaEntityFactory {

	private static final Logger LOG = LogManager.getLogger(NpcBestiaEntityFactory.class);

	private final Archetype npcBestiaArchetype;
	private final NpcBestiaMapper mapper;
	private final World world;
	private final String zoneName;

	public NpcBestiaEntityFactory(String zoneName, World world, NpcBestiaMapper mapper) {

		this.zoneName = zoneName;
		this.world = world;

		npcBestiaArchetype = new ArchetypeBuilder()
				.add(Position.class)
				.add(MobGroup.class)
				.add(PositionDomainProxy.class)
				.add(Attacks.class)
				.add(net.bestia.zoneserver.ecs.component.Bestia.class)
				.add(NPCBestia.class)
				.add(StatusPoints.class)
				.add(Visible.class)
				.add(AI.class)
				.build(world);

		this.mapper = mapper;
	}

	public NpcBestiaEntityProxy create(MobSpawn mobSpawn) {

		final Bestia spawnBestia = mobSpawn.mob;
		final String group = mobSpawn.getGroup();
		final Vector2 pos = mobSpawn.coordinates;

		return create(spawnBestia, pos, group);
	}

	public NpcBestiaEntityProxy create(Bestia bestia, Vector2 position) {

		return create(bestia, position, "none");
	}

	/**
	 * Creates an {@link NpcBestiaEntityProxy} and spawns it directly to the
	 * given position in the responsible zone.
	 * 
	 * @param bestia
	 * @param position
	 * @param groupName
	 * @return
	 */
	public NpcBestiaEntityProxy create(Bestia bestia, Vector2 position, String groupName) {

		final int entityID = world.create(npcBestiaArchetype);

		final NpcBestiaEntityProxy mobBestia = new NpcBestiaEntityProxy(entityID, bestia, mapper);
		mobBestia.getLocation().setMapDbName(zoneName);
		mobBestia.getLocation().setPos(position.x, position.y);
		mapper.getGroupMapper().get(entityID).groupName = groupName;

		LOG.debug("Spawned mob: {}", mobBestia.toString());

		return mobBestia;
	}
}
