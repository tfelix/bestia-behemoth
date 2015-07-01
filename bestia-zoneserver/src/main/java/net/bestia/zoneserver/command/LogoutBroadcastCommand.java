package net.bestia.zoneserver.command;

import java.util.Set;

import net.bestia.messages.BestiaLogoutMessage;
import net.bestia.messages.LogoutBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.ecs.ECSInputControler;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;

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

		ECSInputControler controller = ctx.getServer().getInputController();

		final Account acc = ctx.getServiceLocator().getBean(AccountDAO.class).find(message.getAccountId());

		// Get all bestias from the zone.
		Set<PlayerBestiaManager> activeBestias = controller.getActiveBestias(acc.getId());

		// Re-adress the message to each of this bestia and send to the ECS as poison pill.
		for (PlayerBestiaManager pbm : activeBestias) {
			BestiaLogoutMessage logoutMsg = new BestiaLogoutMessage(message, pbm.getBestia().getId());
			controller.sendInput(logoutMsg);
		}
	}

	@Override
	public String toString() {
		return "LogoutBroadcastCommand[]";
	}

}
