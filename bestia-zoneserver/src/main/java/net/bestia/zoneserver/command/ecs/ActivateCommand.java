package net.bestia.zoneserver.command.ecs;

import com.artemis.ComponentMapper;

import net.bestia.messages.Message;
import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.messages.bestia.BestiaActivatedMessage;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.proxy.InventoryProxy;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;

public class ActivateCommand extends ECSCommand {

	private ComponentMapper<PlayerBestia> playerMapper;
	private ComponentMapper<Active> activeMapper;

	@Override
	public String handlesMessageId() {
		return BestiaActivateMessage.MESSAGE_ID;
	}

	@Override
	protected void initialize() {
		playerMapper = world.getMapper(PlayerBestia.class);
		activeMapper = world.getMapper(Active.class);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		final BestiaActivateMessage msg = (BestiaActivateMessage) message;
		final AccountRegistry register = ctx.getAccountRegistry();

		final PlayerBestiaEntityProxy playerBestia = playerMapper.get(player).playerBestiaManager;
		final int pbId = playerBestia.getPlayerBestiaId();
		final long accId = msg.getAccountId();

		if (pbId == msg.getPlayerBestiaId()) {
			// This bestia should be marked as active.
			player.edit().create(Active.class);

			register.setActiveBestia(accId, pbId);

			// Send the client the currently selected bestia.
			final BestiaActivatedMessage response = new BestiaActivatedMessage(msg);
			ctx.getServer().sendMessage(response);

			// Send the current inventory of this bestia to the client.
			final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
			final InventoryProxy invManager = new InventoryProxy(playerBestia, invService, ctx.getServer());
			final Message invListMessage = invManager.getInventoryListMessage();
			ctx.getServer().sendMessage(invListMessage);
			
		} else {
			if (activeMapper.has(player)) {
				// This bestia should not be active anymore.
				player.edit().remove(Active.class);
			}
		}
	}

	@Override
	public String toString() {
		return "ActivateCommand[]";
	}

}
