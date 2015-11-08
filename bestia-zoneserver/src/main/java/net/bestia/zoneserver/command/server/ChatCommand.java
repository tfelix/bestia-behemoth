package net.bestia.zoneserver.command.server;

import net.bestia.messages.ChatEchoMessage;
import net.bestia.messages.ChatEchoMessage.EchoCode;
import net.bestia.messages.ChatMessage;
import net.bestia.messages.InputMessage;
import net.bestia.messages.InputWrapperMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.command.chat.ChatCommandExecutor;
import net.bestia.zoneserver.ecs.ActiveBestiaRegistry;

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
	private static final ChatCommandExecutor commandFactory = new ChatCommandExecutor();

	@Override
	public void execute(Message message, CommandContext ctx) {
		
		final ChatMessage m = (ChatMessage) message;
		final AccountDAO accDAO = ctx.getServiceLocator().getBean(AccountDAO.class);

		// Find the player who send the message.
		final Account acc = accDAO.find(m.getAccountId());
		final long accId = acc.getId();

		// Set the username of the message to this player.
		m.setSenderNickname(acc.getName());

		final ActiveBestiaRegistry register = ctx.getServer().getActiveBestiaRegistry();
		final int activeBestiaId = register.getActiveBestia(accId);

		if (activeBestiaId == 0) {
			// No bestia was active. Do nothing. Actually this should not happen
			// since the server should not listen to accounts with non active bestias.
			// but better be sure.
			return;
		}

		final ChatEchoMessage replyMsg;
		
		// Check chat type.
		switch (m.getChatMode()) {
		case COMMAND:
			// Check which command the user wanted. If his user level is high
			// enough execute the command.
			commandFactory.execute(m, ctx);
			break;
		
		case PUBLIC:
			// Send the message to all active player in the range so send to the ecs 
			// since we must make sight tests.
			final InputMessage pcm = new InputWrapperMessage<ChatMessage>(m, activeBestiaId);
			ctx.getServer().getMessageRouter().processMessage(pcm);
			
			// Echo the message back to the user.
			replyMsg = ChatEchoMessage.getEchoMessage(m);
			replyMsg.setEchoCode(EchoCode.OK);
			ctx.getServer().sendMessage(replyMsg);
			break;
		
		case PARTY:
		case GUILD:
			// not supported atm.
			log.warn("Guild or Party msg not supported atm.");
			break;
		default:
			// Command will not be handled since these command types
			// dont come from the user client.
			log.error("Malformed ChatMessage: {}", message.toString());
			return;
		}
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
