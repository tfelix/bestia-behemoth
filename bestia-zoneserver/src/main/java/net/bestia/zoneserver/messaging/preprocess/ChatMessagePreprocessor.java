package net.bestia.zoneserver.messaging.preprocess;

import net.bestia.messages.ChatMessage;
import net.bestia.messages.InputWrapperMessage;
import net.bestia.messages.ChatMessage.Mode;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;

/**
 * If a chat message needs the knowledge of a besta (public chat message for
 * example needs the knowledge of the currently active bestia). Then the message
 * will get wrapped and the needed information will be added.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ChatMessagePreprocessor extends MessagePreprocessor {

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

		if (mode == Mode.COMMAND || mode == Mode.PUBLIC) {
			// Find the currently active player bestia.
			final int playerBestiaId = ctx.getServer().getActiveBestiaRegistry().getActiveBestia(msg.getAccountId());
			InputWrapperMessage<ChatMessage> wrappedMsg = new InputWrapperMessage<ChatMessage>(msg, playerBestiaId);
			return wrappedMsg;
		} else {
			return message;
		}
	}

}
