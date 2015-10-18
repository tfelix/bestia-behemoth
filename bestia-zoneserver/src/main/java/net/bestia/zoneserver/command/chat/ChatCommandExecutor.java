package net.bestia.zoneserver.command.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.bestia.messages.ChatMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.util.PackageLoader;

/**
 * This class automatically loads all chat command objects and executed the
 * correct one if a chat command is received. Since the instances of the
 * {@link ChatUserCommand} are statically saved the instances must be immutable
 * in order to be thread safe.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ChatCommandExecutor {

	private final static Map<String, ChatUserCommand> commands = new HashMap<>();

	static {
		final PackageLoader<ChatUserCommand> loader = new PackageLoader<>(ChatUserCommand.class,
				"net.bestia.zoneserver.command.chat");
		final Set<ChatUserCommand> foundCommands = loader.getSubObjects();

		for (ChatUserCommand cmd : foundCommands) {
			final String key = cmd.getChatToken();
			if (commands.containsKey(key)) {
				throw new IllegalStateException(String.format(
						"Command responsible for %s already loaded. Only one command instance for one chat command.",
						key));
			}
			commands.put(key, cmd);
		}
	}

	public ChatCommandExecutor() {

	}

	public void execute(ChatMessage m, CommandContext ctx) {

		final String key = getCommandKey(m.getText());

		if (!commands.containsKey(key)) {
			return;
		}

		commands.get(key).execute(m, ctx);

	}

	private String getCommandKey(String text) {
		String[] tokens = text.split(" ");

		if (tokens.length == 1) {
			return null;
		}

		if (!tokens[1].startsWith("/")) {
			return null;
		}

		return tokens[1];
	}
}
