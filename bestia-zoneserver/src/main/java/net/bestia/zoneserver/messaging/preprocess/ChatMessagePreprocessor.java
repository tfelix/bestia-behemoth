package net.bestia.zoneserver.messaging.preprocess;

import net.bestia.messages.ChatMessage;
import net.bestia.messages.Message;
import net.bestia.messages.MessageIdDecorator;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.command.CommandContext;

/**
 * If a chat message needs the knowledge of a besta (public chat message for
 * example needs the knowledge of the currently active bestia). Then the message
 * will get wrapped and the needed information will be added. Additionally the
 * chat message will be altered since there are different commands for differnt
 * kind of chat messages.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ChatMessagePreprocessor extends MessagePreprocessor {
	
	/**
	 * Messages which should be routed to the ecs.
	 */
	public static final String CHAT_MSG_ID_ECS = "chat_message.ecs";
	
	/**
	 * Messages which sould be handled by the server.
	 */
	public static final String CHAT_MSG_ID_SERVER = "chat_message.server";

	public ChatMessagePreprocessor(CommandContext ctx) {
		super(ctx);
		// no op.
	}

	@Override
	public Message process(Message message) {
		if (!(message instanceof ChatMessage)) {
			return message;
		}

		// If its a chat message see if its of a kind which needs further
		// attention.
		final ChatMessage msg = (ChatMessage) message;

		final ChatMessage.Mode mode = msg.getChatMode();

		// In some cases we need to add the msg sender nickname.
		switch (mode) {
		case PUBLIC:
		case GUILD:
		case PARTY:
			final AccountDAO accDAO = ctx.getServiceLocator().getBean(AccountDAO.class);
			// Find the player who send the message.
			final Account acc = accDAO.findOne(msg.getAccountId());
			msg.setSenderNickname(acc.getName());
			break;
		default:
			// no op.
			break;
		}
		
		// No redirect the messages to the right place.
		switch (mode) {
		case PUBLIC:
		case COMMAND:
			// ECS.
			message = new MessageIdDecorator<Message>(msg, CHAT_MSG_ID_ECS);
			break;
		case GUILD:
		case PARTY:
		case WHISPER:
			message = new MessageIdDecorator<Message>(msg, CHAT_MSG_ID_SERVER);
			break;
		default:
			// no op.
			break;
		}

		return message;
	}

}
