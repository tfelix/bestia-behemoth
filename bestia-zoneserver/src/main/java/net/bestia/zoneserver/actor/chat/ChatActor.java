package net.bestia.zoneserver.actor.chat;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.ClientMessageDigestActor;
import net.bestia.zoneserver.chat.ChatCommandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * This actor processes chat messages from the clients to the bestia system. It
 * will check the preconditions and redirect it to the appropriate receivers
 * which will then in turn work with the chat message or redirect it to the chat
 * command system.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ChatActor extends ClientMessageDigestActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "chat";

	private final ChatCommandService chatCmdService;

	private ActorRef publicChatActor;
	private ActorRef whisperChatActor;
	private ActorRef guildChatActor;
	private ActorRef partyChatActor;

	@Autowired
	public ChatActor(ChatCommandService chatCmdService) {

		this.chatCmdService = Objects.requireNonNull(chatCmdService);

		publicChatActor = SpringExtension.actorOf(getContext(), PublicChatActor.class);
		whisperChatActor = SpringExtension.actorOf(getContext(), WhisperChatActor.class);
		partyChatActor = SpringExtension.actorOf(getContext(), PartyChatActor.class);
		guildChatActor = SpringExtension.actorOf(getContext(), GuildChatActor.class);
		this.redirectConfig.match(ChatMessage.class, this::onChatMessage);
	}

	private void onChatMessage(ChatMessage chatMsg) {
		if (chatCmdService.isChatCommand(chatMsg.getText())) {
			chatCmdService.executeChatCommand(chatMsg.getAccountId(), chatMsg.getText());
			return;
		}

		switch (chatMsg.getChatMode()) {
		case PUBLIC:
			publicChatActor.tell(chatMsg, getSelf());
			break;
		case WHISPER:
			whisperChatActor.tell(chatMsg, getSelf());
			break;
		case PARTY:
			partyChatActor.tell(chatMsg, getSelf());
			break;
		case GUILD:
			guildChatActor.tell(chatMsg, getSelf());
			break;

		default:
			LOG.warning("Message type not yet supported.");
			break;
		}
	}
}