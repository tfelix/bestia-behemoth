package net.bestia.zoneserver.actor.chat;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;
import net.bestia.zoneserver.chat.ChatCommandService;

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
public class ChatActor extends BestiaRoutingActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "chat";

	private final ChatCommandService chatCmdService;
	
	private ActorRef publicChatActor;
	private ActorRef whisperChatActor;
	private ActorRef guildChatActor;
	private ActorRef partyChatActor;

	@Autowired
	public ChatActor(ChatCommandService chatCmdService) {
		super(Arrays.asList(ChatMessage.class));

		this.chatCmdService = Objects.requireNonNull(chatCmdService);
	}

	@Override
	public void preStart() throws Exception {
		publicChatActor = SpringExtension.actorOf(getContext(), PublicChatActor.class);
		whisperChatActor = SpringExtension.actorOf(getContext(), WhisperChatActor.class);
		partyChatActor = SpringExtension.actorOf(getContext(), PartyChatActor.class);
		guildChatActor = SpringExtension.actorOf(getContext(), GuildChatActor.class);
		
		// Register for chat commands.
		final RedirectMessage redirMsg = RedirectMessage.get(ChatMessage.class);
		getContext().parent().tell(redirMsg, getSelf());
	}

	@Override
	protected void handleMessage(Object msg) {

		final ChatMessage chatMsg = (ChatMessage) msg;

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
