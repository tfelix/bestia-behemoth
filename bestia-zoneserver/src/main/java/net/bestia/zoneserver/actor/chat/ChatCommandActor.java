package net.bestia.zoneserver.actor.chat;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.chat.ChatCommand;

/**
 * This actor processes chat messages from the clients to the bestia system. It
 * will check the preconditions and redirect it to the apropriate receivers.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class ChatCommandActor extends BestiaActor {

	public static final String NAME = "chatCmd";
	public static final String CMD_PREFIX = "/";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final List<ChatCommand> chatCommands;

	/**
	 * The actor always tries to find all implementations of {@link ChatCommand}
	 * and loads them upon creation. All the incoming chat commands are tested
	 * for this input.
	 * 
	 * @param chatCommands
	 *            A list of all chat command implementations.
	 */
	@Autowired
	public ChatCommandActor(List<ChatCommand> chatCommands) {

		this.chatCommands = Objects.requireNonNull(chatCommands);
	}

	@Override
	public void onReceive(Object msg) throws Throwable {
		if (!(msg instanceof ChatMessage)) {
			unhandled(msg);
			return;
		}

		final ChatMessage chatMsg = (ChatMessage) msg;

		// First small check if we have potentially a command or if the can stop
		// right away.
		if (!chatMsg.getText().startsWith(CMD_PREFIX)) {
			return;
		}

		chatCommands.stream()
				.filter(x -> x.isCommand(chatMsg.getText()))
				.forEach(cmd -> {
					LOG.info("Chat command: {}, Message: {}", cmd.toString(), chatMsg.toString());
					cmd.executeCommand(chatMsg.getAccountId(), chatMsg.getText());
				});
	}
}
