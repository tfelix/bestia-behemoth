package net.bestia.zoneserver.command.ecs;

import com.artemis.ComponentMapper;

import net.bestia.messages.BestiaActivateMessage;
import net.bestia.messages.Message;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.BestiaActiveRegister;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.manager.InventoryManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

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
		final BestiaActiveRegister register = ctx.getServer().getBestiaRegister();
		
		final PlayerBestiaManager playerBestia = playerMapper.get(player).playerBestiaManager;
		final int pbId = playerBestia.getPlayerBestiaId();
		final long accId = msg.getAccountId();

		if (pbId == msg.getPlayerBestiaId()) {
			// This bestia should be marked as active.
			player.edit().create(Active.class);
			
			register.setActiveBestia(accId, pbId);
			
			// TODO BUG Update the Client via msg to the latest activated bestia.
			
			// Send the current inventory of this bestia to the client.
			final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
			final InventoryManager invManager = new InventoryManager(playerBestia, invService, ctx.getServer());
			final Message invListMessage = invManager.getInventoryListMessage();
			ctx.getServer().processMessage(invListMessage);
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
