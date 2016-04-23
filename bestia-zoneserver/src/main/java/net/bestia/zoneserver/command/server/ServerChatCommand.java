package net.bestia.zoneserver.command.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.Message;
import net.bestia.messages.MessageIdDecorator;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;

/**
 * Processes inter-server chat commands like party or guild chats.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public class ServerChatCommand extends Command {

	private static final Logger log = LogManager.getLogger(ServerChatCommand.class);

	@Override
	public void execute(Message message, CommandContext ctx) {
		@SuppressWarnings("unchecked")
		final ChatMessage m = ((MessageIdDecorator<ChatMessage>) message).getMessage();


		// Check chat type.
		switch (m.getChatMode()) {
		case PARTY:
		case GUILD:
		case WHISPER:
			// not supported atm.
			log.warn("Guild or Party msg not supported atm.");
			ChatMessage msg = ChatMessage.getEchoRawMessage(m.getAccountId(),
					"Guild or party chats not supported at the moment.");
			ctx.getServer().sendMessage(msg);
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
		return "GuildPartyChatCommand[]";
	}

	@Override
	public String handlesMessageId() {
		return "";
		//return ChatMessagePreprocessor.CHAT_MSG_ID_SERVER;
	}

}
