package net.bestia.zoneserver.ecs.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.annotations.Wire;

import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.domain.Bestia;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.AI;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.component.MobGroup;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.StatusPoints;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.proxy.NpcEntityProxy;

/**
 * Factory for the creation of {@link NpcEntityProxy}s.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
class MobEntityFactory extends EntityFactory {

	private static final Logger LOG = LogManager.getLogger(MobEntityFactory.class);

	private final Archetype npcBestiaArchetype;

	@Wire
	private ComponentMapper<MobGroup> mobGroupMapper;
	
	private final BestiaDAO dao;

	public MobEntityFactory(World world, CommandContext ctx) {
		super(world, ctx);

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
		
		dao = ctx.getServiceLocator().getBean(BestiaDAO.class);
	}

	/**
	 * Creates an {@link NpcEntityProxy} and spawns it directly to the given
	 * position in the responsible zone.
	 * 
	 * @param bestia
	 * @param position
	 * @param groupName
	 * @return
	 */
	@Override
	public void spawn(EntityBuilder builder) {
		
		final int entityID = world.create(npcBestiaArchetype);
		
		// Get the bestia from the name of the mob.
		final Bestia bestia = dao.findByDatabaseName(builder.mobName);	
		final NpcEntityProxy mobBestia = new NpcEntityProxy(world, entityID, bestia);
		
		mobBestia.getLocation().setPos(builder.position.x, builder.position.y);
		mobGroupMapper.get(entityID).groupName = builder.mobGroup;

		LOG.debug("Spawned mob: {}", mobBestia.toString());
	}

	@Override
	public boolean canSpawn(EntityBuilder builder) {
		// We need to check several data.
		if(builder.mobName == null || builder.mobName.isEmpty()) {
			return false;
		}
		
		return true;
	}
}
