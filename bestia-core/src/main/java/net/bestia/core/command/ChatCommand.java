package net.bestia.core.command;

import net.bestia.core.message.ChatEchoMessage;
import net.bestia.core.message.ChatEchoMessage.EchoCode;
import net.bestia.core.message.ChatMessage;
import net.bestia.core.message.Message;

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
	private ChatMessage message;

	/**
	 * Ctor.
	 * 
	 * @param message
	 *            The chat message from the user to the server.
	 * @param serviceFactory
	 * @param queue
	 */
	public ChatCommand(Message message, CommandContext context) {
		super(message, context);

		if (!(message instanceof ChatMessage)) {
			throw new IllegalArgumentException("Mesage is not the correc type.");
		}
		this.message = (ChatMessage) message;
	}

	@Override
	protected PreExecutionCheck validateExecution() {
		return PreExecutionCheck.OK;
	}

	@Override
	protected void executeCommand() {
		// Check chat type.
		switch (message.getChatMode()) {
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
		ChatEchoMessage msg = ChatEchoMessage.getEchoMessage(message);
		msg.setEchoCode(EchoCode.OK);
		sendMessage(msg);
	}

}
