package net.bestia.zoneserver.command;

import net.bestia.messages.LogoutBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.ecs.InputController;

/**
 * Executes if a logout broadcast message is issued. This message will be send from the webserver if an error happens or
 * a connections drops for this particular user (if he closes the browser e.g.) then its up to the server to persist all
 * data to the database and remove its bestia after a certain cooldown time.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LogoutBroadcastCommand extends Command {

	@Override
	public String handlesMessageId() {
		return LogoutBroadcastMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		InputController controller = ctx.getServer().getInputController();

		// Simply remove the bestia from the input controller. The ECS has to register itself to the handler to react
		// upon removal.
		controller.removeAccount(message.getAccountId());
	}

	@Override
	public String toString() {
		return "LogoutBroadcastCommand[]";
	}

}
