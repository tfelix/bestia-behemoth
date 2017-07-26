package net.bestia.zoneserver.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The service always tries to find all implementations of {@link ChatCommand}
 * and loads them upon creation. All the incoming chat commands are tested for
 * this input.
 *
 */
@Service
public class ChatCommandService {

	private static final Logger LOG = LoggerFactory.getLogger(ChatCommandService.class);

	public static final String CMD_PREFIX = "/";

	private final List<ChatCommand> chatCommands = new ArrayList<>();

	@Autowired
	public ChatCommandService(List<ChatCommand> chatCommands) {

		this.chatCommands.addAll(Objects.requireNonNull(chatCommands));
	}

	/**
	 * Checks if the given text string contains a chat command.
	 * 
	 * @param text
	 *            The chat text to check against a command.
	 * @return TRUE if it contains a executable command. FALSE otherwise.
	 */
	public boolean isChatCommand(String text) {
		return text.startsWith(CMD_PREFIX);
	}

	/**
	 * Executes the chat command in the context of the given account id.
	 * 
	 * @param accId
	 *            The account who is executing this chat command.
	 * @param text
	 *            The text containing the command.
	 */
	public void executeChatCommand(long accId, String text) {
		LOG.debug("Account {} used chat command. Message: {}", accId, text);

		// First small check if we have potentially a command or if the can stop
		// right away.
		if (!isChatCommand(text)) {
			return;
		}

		chatCommands.stream()
				.filter(x -> x.isCommand(text))
				.findFirst()
				.ifPresent(cmd -> cmd.executeCommand(accId, text));
	}
}
