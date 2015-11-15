package net.bestia.zoneserver.command.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.ChatMessage;
import net.bestia.messages.ChatMessage.Mode;
import net.bestia.messages.Message;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;

/**
 * Processes inter-server chat commands like party or guild chats.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public class GuildPartyChatCommand extends Command {

	private static final Logger log = LogManager.getLogger(GuildPartyChatCommand.class);

	@Override
	public void execute(Message message, CommandContext ctx) {
		final ChatMessage m = (ChatMessage) message;

		// We are only interested in guild or party chats (global chats). The
		// other chat modes must be handled by the ECS.
		if (m.getChatMode() != Mode.GUILD && m.getChatMode() != Mode.PARTY) {
			return;
		}

		// Check chat type.
		switch (m.getChatMode()) {
		case PARTY:
		case GUILD:
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
		return ChatMessage.MESSAGE_ID;
	}

}
