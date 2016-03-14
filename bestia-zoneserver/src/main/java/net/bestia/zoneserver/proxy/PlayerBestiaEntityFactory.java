package net.bestia.zoneserver.proxy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.Entity;
import com.artemis.World;

import net.bestia.messages.Message;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.component.NPCBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.PositionDomainProxy;
import net.bestia.zoneserver.ecs.component.StatusPoints;
import net.bestia.zoneserver.ecs.component.Visible;

public class PlayerBestiaEntityFactory {
	
	private final static Logger LOG = LogManager.getLogger(PlayerBestiaEntityFactory.class);
	
	private final Archetype playerBestiaArchetype;
	private final PlayerBestiaMapper mapper;
	private final World world;

	public PlayerBestiaEntityFactory(World world, PlayerBestiaMapper mapper) {

		this.world = world;

		playerBestiaArchetype = new ArchetypeBuilder()
				.add(Position.class)
				.add(PositionDomainProxy.class)
				.add(Attacks.class)
				.add(net.bestia.zoneserver.ecs.component.Bestia.class)
				.add(NPCBestia.class)
				.add(StatusPoints.class)
				.add(Visible.class)
				.build(world);

		this.mapper = mapper;
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
	public PlayerBestiaEntityProxy create(PlayerBestia bestia) {
		
		final int entityId = world.create(playerBestiaArchetype);
		final Entity entity = world.getEntity(entityId);

		final PlayerBestiaEntityProxy pbProxy = new PlayerBestiaEntityProxy(entity, bestia, mapper);
		
		// We need to check the bestia if its the master bestia. It will get marked as active initially.
		final PlayerBestia master = bestia.getOwner().getMaster();
		final boolean isMaster = master.equals(bestia);

		if (isMaster) {
			// Spawn the master as active bestia.
			// TODO Das gibt probleme wenn der master die map wechselt und als
			// aktiv neu markiert wird. Dies sollte nur mit einer LoginMessage
			// passieren.
			entity.edit().create(Active.class);

			final InventoryService invService = mapper.getLocator().getBean(InventoryService.class);
			final InventoryProxy invManager = new InventoryProxy(pbProxy, invService, mapper.getServer());
			final Message invListMessage = invManager.getInventoryListMessage();
			mapper.getServer().sendMessage(invListMessage);
		}

		// Send a update to client so he can pick up the new bestia.
		final BestiaInfoMessage infoMsg = new BestiaInfoMessage();
		infoMsg.setAccountId(pbProxy.getAccountId());
		infoMsg.setBestia(pbProxy.getPlayerBestia(), pbProxy.getStatusPoints());
		infoMsg.setIsMaster(isMaster);
		mapper.getServer().sendMessage(infoMsg);
		
		// Now set all the needed values.
		LOG.trace("Spawning player bestia: {}.", bestia);
		
		return pbProxy;
	}
}
