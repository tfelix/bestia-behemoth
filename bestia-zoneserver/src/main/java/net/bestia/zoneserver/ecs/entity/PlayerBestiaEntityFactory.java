package net.bestia.zoneserver.ecs.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Archetype;
import com.artemis.ArchetypeBuilder;
import com.artemis.Entity;
import com.artemis.World;
import com.artemis.annotations.Wire;

import net.bestia.messages.Message;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.StatusPoints;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.proxy.InventoryProxy;
import net.bestia.zoneserver.proxy.NpcEntityProxy;
import net.bestia.zoneserver.proxy.PlayerEntityProxy;

/**
 * This factory is responsible for spawning player bestias into the ECS.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
class PlayerBestiaEntityFactory extends EntityFactory {

	private final static Logger LOG = LogManager.getLogger(PlayerBestiaEntityFactory.class);

	private final Archetype playerBestiaArchetype;

	@Wire
	private CommandContext ctx;

	public PlayerBestiaEntityFactory(World world, CommandContext ctx) {
		super(world, ctx);

		world.inject(this);

		playerBestiaArchetype = new ArchetypeBuilder()
				.add(Position.class)
				// .add(PositionDomainProxy.class)
				.add(Attacks.class)
				.add(net.bestia.zoneserver.ecs.component.Bestia.class)
				.add(net.bestia.zoneserver.ecs.component.PlayerBestia.class)
				.add(StatusPoints.class)
				.add(Visible.class)
				.build(world);
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
	@Override
	public void spawn(EntityBuilder builder) {
		final PlayerEntityBuilder playerBuilder = (PlayerEntityBuilder) builder;

		final int entityId = world.create(playerBestiaArchetype);
		final Entity entity = world.getEntity(entityId);

		final PlayerEntityProxy pbProxy = new PlayerEntityProxy(world, entity, playerBuilder.playerBestia);

		// We need to check the bestia if its the master bestia. It will get
		// marked as active initially.
		final PlayerBestia master = playerBuilder.playerBestia.getOwner().getMaster();
		final boolean isMaster = master.equals(playerBuilder.playerBestia);

		if (isMaster) {
			// Spawn the master as active bestia.
			// TODO Das gibt probleme wenn der master die map wechselt und als
			// aktiv neu markiert wird. Dies sollte nur mit einer LoginMessage
			// passieren.
			pbProxy.setActive(true);

			final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
			final InventoryProxy invManager = new InventoryProxy(pbProxy, invService, ctx.getServer());
			final Message invListMessage = invManager.getInventoryListMessage();
			ctx.getServer().sendMessage(invListMessage);
		}

		// Send a update to client so he can pick up the new bestia.
		final BestiaInfoMessage infoMsg = new BestiaInfoMessage();
		infoMsg.setAccountId(pbProxy.getAccountId());
		infoMsg.setBestia(pbProxy.getPlayerBestia(), pbProxy.getStatusPoints());
		infoMsg.setIsMaster(isMaster);
		ctx.getServer().sendMessage(infoMsg);

		// Now set all the needed values.
		LOG.trace("Spawning player bestia: {}.", playerBuilder.playerBestia);
	}

	@Override
	public boolean canSpawn(EntityBuilder builder) {
		return builder instanceof PlayerEntityBuilder;
	}
}
