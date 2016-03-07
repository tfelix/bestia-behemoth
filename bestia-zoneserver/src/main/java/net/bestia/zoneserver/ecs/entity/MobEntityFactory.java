package net.bestia.zoneserver.ecs.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.World;

import net.bestia.zoneserver.ecs.component.AI;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.StatusPoints;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.manager.NpcBestiaEntityProxy;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Factory class for building mob entities and placing them into the zone.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MobEntityFactory {
	
	private static final Logger LOG = LogManager.getLogger(MobEntityFactory.class);

	private final ComponentMapper<MobGroup> groupMapper;
	// private ComponentMapper<AI> aiMapper;
	private final ComponentMapper<Bestia> bestiaMapper;
	private final ComponentMapper<Position> positionMapper;
	private final ComponentMapper<Visible> visibleMapper;
	private final ComponentMapper<NPCBestia> npcBestiaMapper;
	private final ComponentMapper<StatusPoints> statusMapper;
	private final Archetype npcBestiaArchtype;

	private final World world;

	public MobEntityFactory(World world) {
		this.world = world;

		npcBestiaArchtype = new ArchetypeBuilder()
				.add(Visible.class)
				.add(AI.class)
				.add(Bestia.class)
				.add(NPCBestia.class)
				.add(MobGroup.class)
				.add(Position.class)
				.build(world);

		this.bestiaMapper = world.getMapper(Bestia.class);
		this.positionMapper = world.getMapper(Position.class);
		this.visibleMapper = world.getMapper(Visible.class);
		this.npcBestiaMapper = world.getMapper(NPCBestia.class);
		this.statusMapper = world.getMapper(StatusPoints.class);
		this.groupMapper = world.getMapper(MobGroup.class);
	}

	/**
	 * Creates the bestia as entity.
	 * 
	 * @param bestia
	 * @return
	 */
	public int create(net.bestia.model.domain.Bestia bestia, String groupName, Vector2 position) {
		final int mob = world.create(npcBestiaArchtype);
		final Entity mobEntity = world.getEntity(mob);

		groupMapper.get(mob).groupName = groupName;

		final Position pos = positionMapper.get(mob);
		pos.position = new Vector2(position.x, position.y);

		final NpcBestiaEntityProxy npcManager = new NpcBestiaEntityProxy(bestia, world, mobEntity);
		bestiaMapper.get(mob).bestiaManager = npcManager;
		npcBestiaMapper.get(mob).manager = npcManager;
		statusMapper.get(mob).statusPoints = npcManager.getStatusPoints();

		// Set the sprite name.
		visibleMapper.get(mob).sprite = bestia.getDatabaseName();
		
		LOG.trace("Spawned mob: {}, entity id: {}", bestia.getDatabaseName(), mob);
		
		return mob;
	}

}
