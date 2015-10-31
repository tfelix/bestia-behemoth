package net.bestia.zoneserver.command;

import net.bestia.messages.InputMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.ecs.BestiaRegister;
import net.bestia.zoneserver.manager.PlayerBestiaManagerInterface;

/**
 * Sends a {@link InputMessage} directly to the ECS where a own command infrastructure will take care about the
 * execution of this command.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InputCommand extends Command {

	/**
	 * This command will not constructed via its ID but via class inheritance.
	 * @see RoutedECSCommandFactory
	 */
	@Override
	public String handlesMessageId() {
		// Creation of this command not via its id but via class inheritance.
		// See RoutedECSCommandFactory.
		return "NO_MESSAGE_HAS_THIS_ID";
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		InputMessage msg = (InputMessage) message;
		
		final BestiaRegister register = ctx.getServer().getBestiaRegister();
		final PlayerBestiaManagerInterface pb = register.getSpawnedBestia(msg.getAccountId(), msg.getPlayerBestiaId());
		
		// Sanity check if the account actually has the given bestia id.
		if(pb == null) {
			return;
		}
		
		final String activeZone = pb.getLocation().getMapDbName();
		
		ctx.getServer().getZone(activeZone).sendInput(msg);
	}

}
