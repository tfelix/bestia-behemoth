package net.bestia.zoneserver.ecs.command;

import net.bestia.messages.BestiaActivateMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.InputController;
import net.bestia.zoneserver.ecs.component.Active;

public class ActivateCommand extends ECSCommand {

	@Override
	public String handlesMessageId() {
		return BestiaActivateMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		
		final BestiaActivateMessage msg = (BestiaActivateMessage) message;
		final InputController inputController = ctx.getServer().getInputController();
		
		if (playerBestiaId == msg.getActivatePlayerBestiaId()) {
			// This bestia should be marked as active.
			player.edit().create(Active.class);
			
			inputController.setActiveBestia(accountId, playerBestiaId);
			
			// TODO BUG Update the Client via msg to the latest activated bestia.
			
		} else {
			if (activeMapper.has(player)) {
				// This bestia should not be active anymore.
				player.edit().remove(Active.class);
				inputController.unsetActiveBestia(accountId);
			}
		}
	}

}
