package net.bestia.zoneserver.command;

import java.util.Collection;

import net.bestia.messages.ChatEchoMessage;
import net.bestia.messages.ChatMessage;
import net.bestia.messages.Message;
import net.bestia.messages.ChatEchoMessage.EchoCode;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.game.zone.Entity;

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
		Account acc = ctx.getServiceFactory()
				.getAccountServiceFactory()
				.getAccountService(m.getAccountId())
				.getAccount();
		
		// Set the username of the message to this player.
		
		// Get the location of the current active bestia.
		String location = "test-zone1";
		
		// Get the player bestia id and use it to find entity and determine other player entities in range.
		
		// Send the message to all active player in the range. There will be a list of entities.
		
		// Get the account ids.
		Collection<Entity> entities = ctx.getZone(location).getEntities(1, 0);
		entities.forEach((e) -> { redirectMessage(e.accountId, m, ctx); });
		

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

		// Echo the message back to the user.
		//ChatEchoMessage replyMsg = ChatEchoMessage.getEchoMessage(m);
		//replyMsg.setEchoCode(EchoCode.OK);
	}
	
	private void redirectMessage(long receiverId, ChatMessage msg, CommandContext ctx) {
		ctx.getServer().sendMessage(msg.getForwardMessage(receiverId));
	}

	@Override
	public String handlesMessageId() {
		return ChatMessage.MESSAGE_ID;
	}

}
