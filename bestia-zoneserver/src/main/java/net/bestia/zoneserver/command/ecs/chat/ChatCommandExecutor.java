package net.bestia.zoneserver.command.ecs.chat;

import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.chat.ChatMessage;
import net.bestia.messages.chat.ChatMessage.Mode;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.proxy.PlayerEntityProxy;
import net.bestia.zoneserver.util.PackageLoader;

/**
 * Gets the chat command commands depending on the concrete issued chat command.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ChatCommandExecutor {
	
	private static final Logger LOG = LogManager.getLogger(ChatCommandExecutor.class);

	private final Set<ChatUserCommand> chatCommands;

	public ChatCommandExecutor() {
		final PackageLoader<ChatUserCommand> cmdLoader = new PackageLoader<>(ChatUserCommand.class,
				"net.bestia.zoneserver.command.ecs.chat");

		chatCommands = cmdLoader.getSubObjects();
	}

	public void execute(ChatMessage msg, PlayerEntityProxy player, CommandContext ctx) {

		if (msg.getChatMode() != Mode.COMMAND) {
			return;
		}
		
		final AccountDAO accDAO = ctx.getServiceLocator().getBean(AccountDAO.class);
		
		// Find the player who send the message.
				final Account acc = accDAO.findOne(msg.getAccountId());

		// Check if we have a suitable command.
		for (ChatUserCommand cmd : chatCommands) {
			if (msg.getText().startsWith(cmd.getChatToken())) {
				
				if (acc.getUserLevel().compareTo(cmd.getNeededUserLevel()) < 0) {
					// User level not high enough.
					LOG.debug("User level not high enough for command: {}, accId: {}", cmd.toString(), acc.getId());
					return;
				}
				
				cmd.execute(msg, player, ctx);

			}
		}

	}

}
