package net.bestia.zoneserver.ecs.command;

import net.bestia.messages.InventoryItemUseMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;

public class UseItemCommand extends Command {

	@Override
	public String handlesMessageId() {
		return InventoryItemUseMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		// TODO Auto-generated method stub
	}

}
