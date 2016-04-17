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
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.StatusPoints;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.proxy.NpcBestiaEntityProxy;
import net.bestia.zoneserver.proxy.NpcBestiaEntityProxy2;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Factory for the creation of {@link NpcBestiaEntityProxy}s.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class NpcBestiaEntityFactory2 extends EntityFactory {

	private static final Logger LOG = LogManager.getLogger(NpcBestiaEntityFactory2.class);

	private final Archetype npcBestiaArchetype;
	
	private  ComponentMapper<MobGroup> groupMapper;
	private  ComponentMapper<NPCBestia> npcBestiaMapper;
	private  ComponentMapper<Position> positionMapper;
	private  ComponentMapper<Visible> visibleMapper;
	private  ComponentMapper<Bestia> bestiaMapper;
	private  ComponentMapper<Movement> movementMapper;
	private  ComponentMapper<StatusPoints> statusMapper;
	
	private final String zoneName;

	public NpcBestiaEntityFactory2(String zoneName, World world) {
		super(world);

		this.zoneName = zoneName;
		
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

		// Inject the mappers.
		world.inject(this);
	}

	public NpcBestiaEntityProxy2 create(MobSpawn mobSpawn) {

		final Bestia spawnBestia = mobSpawn.mob;
		final String group = mobSpawn.getGroup();
		final Vector2 pos = mobSpawn.coordinates;

		return create(spawnBestia, pos, group);
	}

	public NpcBestiaEntityProxy2 create(Bestia bestia, Vector2 position) {

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
	public NpcBestiaEntityProxy2 create(Bestia bestia, Vector2 position, String groupName) {

		final int entityID = world.create(npcBestiaArchetype);

		final NpcBestiaEntityProxy2 mobBestia = new NpcBestiaEntityProxy2(entityID, bestia, world);
		
		mobBestia.getLocation().setMapDbName(zoneName);
		mobBestia.getLocation().setPos(position.x, position.y);
		groupMapper.get(entityID).groupName = groupName;
		

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
