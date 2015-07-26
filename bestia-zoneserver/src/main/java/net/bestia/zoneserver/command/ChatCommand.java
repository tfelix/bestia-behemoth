package net.bestia.zoneserver.command;

import java.util.Collection;

import net.bestia.messages.ChatEchoMessage;
import net.bestia.messages.ChatEchoMessage.EchoCode;
import net.bestia.messages.ChatMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Bestia;
import net.bestia.model.service.AccountService;
import net.bestia.zoneserver.ecs.InputController;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Process a chat message from the user. It checks which kind of chat is wished for. The command checks if the chat is
 * valid and if so processes the message if not answers the user with an error. The chat command checks the type of the
 * message depending on the type of the message sending the data back to all receivers in sight (public) all in the same
 * party or all in the same guild.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
class ChatCommand extends Command {

	private static final Logger log = LogManager.getLogger(ChatCommand.class);

	@Override
	public void execute(Message message, CommandContext ctx) {
		ChatMessage m = (ChatMessage) message;

		// Find the player who send the message.
		final Account acc = ctx.getServiceLocator().getBean(AccountDAO.class).find(m.getAccountId());
		final long accId = acc.getId();

		// Set the username of the message to this player.
		m.setSenderNickname(acc.getName());

		final InputController controller = ctx.getServer().getInputController();
		final int activeBestiaId = controller.getActiveBestia(accId);

		if (activeBestiaId == 0) {
			// No bestia was active. Do nothing.
			return;
		}

		// Check chat type.
		switch (m.getChatMode()) {
		case COMMAND:
			// Check which command the user wanted. If his user level is high
			// enough execute the command.
		case PUBLIC:
		case PARTY:
		case GUILD:
			// TODO Handle the other chat types.

			// Since sightests are not available send the message back to all player on the same zone.

			break;
		default:
			// Command will not be handled since these command types
			// dont come from the user client.
			log.error("Malformed ChatMessage: {}", message.toString());
			return;
		}

		// Get the player bestia id and use it to find entity and determine other player entities in range.

		// Send the message to all active player in the range. There will be a list of entities.
		final ChatEchoMessage replyMsg;
		if (controller.sendInput(m, activeBestiaId)) {
			// Echo the message back to the user.
			replyMsg = ChatEchoMessage.getEchoMessage(m);
			replyMsg.setEchoCode(EchoCode.ERROR);
		} else {
			// Echo the message back to the user.
			replyMsg = ChatEchoMessage.getEchoMessage(m);
			replyMsg.setEchoCode(EchoCode.ERROR);
		}

		ctx.getServer().sendMessage(replyMsg);
	}

	private void redirectMessage(long receiverId, ChatMessage msg, CommandContext ctx) {
		ctx.getServer().sendMessage(ChatMessage.getForwardMessage(receiverId, msg));
	}

	@Override
	public String toString() {
		return "ChatCommand[]";
	}

	@Override
	public String handlesMessageId() {
		return ChatMessage.MESSAGE_ID;
	}

}
