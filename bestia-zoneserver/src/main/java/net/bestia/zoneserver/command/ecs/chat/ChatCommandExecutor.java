package net.bestia.zoneserver.command.ecs.chat;

import java.util.Set;

import net.bestia.messages.ChatMessage;
import net.bestia.messages.ChatMessage.Mode;
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

		// Check if we have a suitable command.
		for (ChatUserCommand cmd : chatCommands) {
			if (msg.getText().startsWith(cmd.getChatToken())) {

				cmd.execute(msg, player, ctx);

			}
		}

	}

}
