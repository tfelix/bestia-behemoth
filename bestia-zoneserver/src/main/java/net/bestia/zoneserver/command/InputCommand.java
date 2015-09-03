package net.bestia.zoneserver.command;

import net.bestia.messages.InputMessage;
import net.bestia.messages.Message;

/**
 * Sends a {@link InputMessage} directly to the ECS where a own command infrastructure will take care about the
 * execution of this command.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InputCommand extends Command {

	@Override
	public String handlesMessageId() {
		// Creation of this command not via its id but via class inheritance.
		// See RoutedECSCommandFactory.
		return "NO_MESSAGE_HAS_THIS_ID";
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		InputMessage msg = (InputMessage) message;
		ctx.getServer().getInputController().sendInput(msg);
	}

}
