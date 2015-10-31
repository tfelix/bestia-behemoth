package net.bestia.zoneserver.command;

import java.util.Set;

import net.bestia.messages.BestiaActivateMessage;
import net.bestia.messages.InputMessage;
import net.bestia.messages.InputWrapperMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.ecs.BestiaRegister;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.manager.PlayerBestiaManagerInterface;

/**
 * This command will switch between active bestias. Since it is not known on which zone the bestia resides, this command
 * is send to all zones. The zone holding the current active bestia must make it inactive while the zone holding the new
 * bestia will mark it as active.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 */
public class SwitchActiveCommand extends Command {

	@Override
	public String handlesMessageId() {
		return BestiaActivateMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		// With the filtering of handlesMessageId we are safe to cast directly.
		final BestiaActivateMessage msg = (BestiaActivateMessage) message;
		final BestiaRegister controller = ctx.getServer().getBestiaRegister();

		Set<PlayerBestiaManager> pbms = controller.getSpawnedBestias(message.getAccountId());
		for (PlayerBestiaManagerInterface pbm : pbms) {
			final int pbId = pbm.getPlayerBestiaId();
			final String zoneName = pbm.getLocation().getMapDbName();
			
			// Copy the message since it is not immutable and we should not share the message between threads.
			final InputMessage wrappedMsg = new InputWrapperMessage<BestiaActivateMessage>(msg, pbId);
			ctx.getServer().getZone(zoneName).sendInput(wrappedMsg);
		}
	}

	@Override
	public String toString() {
		return "SwitchActiveCommand[]";
	}
}
