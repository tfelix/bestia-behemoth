package net.bestia.zoneserver.command;

import net.bestia.messages.InputMessage;
import net.bestia.messages.Message;

/**
 * This {@link CommandFactory} will look into the type of the message. If it is an {@link InputMessage} the message will
 * be directed to the ECS via an InputCommand.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class RoutedECSCommandFactory extends CommandFactory {
	
	private CommandContext ctx;

	public RoutedECSCommandFactory(CommandContext ctx) {
		super(ctx);
		this.ctx = ctx;
	}

	/**
	 * Returns a InputCommand if the message was a {@link InputMessage}.
	 * 
	 */
	@Override
	public Command getCommand(Message message) {
		
		if(message instanceof InputMessage) {
			final InputCommand cmd = new InputCommand();
			cmd.setCommandContext(ctx);
			cmd.setMessage(message);
			return cmd;
		}
		
		// Return the normal message.
		return super.getCommand(message);
	}
}
