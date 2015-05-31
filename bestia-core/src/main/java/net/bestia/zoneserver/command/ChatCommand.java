package net.bestia.zoneserver.command;

import net.bestia.messages.ChatEchoMessage;
import net.bestia.messages.ChatMessage;
import net.bestia.messages.Message;
import net.bestia.messages.ChatEchoMessage.EchoCode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Process a chat message from the user. It checks which kind of chat is wished
 * for. The command checks if the chat is valid and if so processes the message
 * if not answers the user with an error.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
class ChatCommand extends Command {

	private static final Logger log = LogManager.getLogger(ChatCommand.class);


	@Override
	public void execute(Message message, CommandContext ctx) {
		ChatMessage m = (ChatMessage)message;
		
		// Check chat type.
		switch (m.getChatMode()) {
		case COMMAND:
			// Check which command the user wanted. If his user level is high
			// enough execute the command.
		case PUBLIC:
		case PARTY:
		case GUILD:
			// TODO Handle the chat types.
			break;
		default:
			// Command will not be handled since these command types
			// dont come from the user client.
			log.error("Malformed ChatMessage: {}", message.toString());
			return;
		}
		
		// Echo the message back to the user.
		ChatEchoMessage replyMsg = ChatEchoMessage.getEchoMessage(m);
		replyMsg.setEchoCode(EchoCode.OK);
		//ctx.getMessenger().sendMessage(replyMsg);
	}


	@Override
	public String handlesMessageId() {
		return ChatMessage.MESSAGE_ID;
	}

}
