package net.bestia.zoneserver.command.ecs;

import com.artemis.ComponentMapper;

import net.bestia.messages.Message;
import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.messages.bestia.BestiaActivatedMessage;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.proxy.InventoryProxy;
import net.bestia.zoneserver.proxy.PlayerEntityProxy;

/**
 * Sets a bestia as the currently active bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ActivateCommand extends ECSCommand {

	private ComponentMapper<PlayerBestia> playerMapper;
	private PlayerBestiaSpawnManager pbManager;

	@Override
	public String handlesMessageId() {
		return BestiaActivateMessage.MESSAGE_ID;
	}

	@Override
	protected void initialize() {
		playerMapper = world.getMapper(PlayerBestia.class);
		pbManager = world.getSystem(PlayerBestiaSpawnManager.class);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		final BestiaActivateMessage msg = (BestiaActivateMessage) message;
		final AccountRegistry register = ctx.getAccountRegistry();

		final PlayerEntityProxy playerBestia = playerMapper.get(player).playerBestia;
		final long accId = msg.getAccountId();

		final int activeBestiaId = register.getActiveBestia(accId);

		// If the bestias are the same do nothing.
		if (msg.getPlayerBestiaId() == activeBestiaId) {
			return;
		}

		// Remove current active bestia.
		final PlayerEntityProxy activePb = pbManager.getPlayerBestiaProxy(activeBestiaId);
		if (activePb != null) {
			activePb.setActive(false);
		}
		
		playerBestia.setActive(true);	

		// Send the client the currently selected bestia.
		final BestiaActivatedMessage response = new BestiaActivatedMessage(msg);
		ctx.getServer().sendMessage(response);

		// Send the current inventory of this bestia to the client.
		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
		final InventoryProxy invManager = new InventoryProxy(playerBestia, invService, ctx.getServer());
		final Message invListMessage = invManager.getInventoryListMessage();
		ctx.getServer().sendMessage(invListMessage);
	}

	@Override
	public String toString() {
		return "ActivateCommand[]";
	}

}
