package net.bestia.zoneserver.actor.zone;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.Message;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.service.AccountZoneService;

/**
 * This actor processes chat messages from the clients to the bestia system. It
 * will check the preconditions and redirect it to the apropriate receivers.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class ChatActor extends BestiaRoutingActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "chat";
	private final Set<Class<? extends Message>> HANDLED_CLASSES = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(ChatMessage.class)));
	
	private final AccountZoneService accService;
	private final ActorRef responder;
	
	@Autowired
	public ChatActor(AccountZoneService accService) {
		
		this.accService = Objects.requireNonNull(accService);
		this.responder = createActor(SendClientActor.class);
	}

	@Override
	protected Set<Class<? extends Message>> getHandledMessages() {
		return HANDLED_CLASSES;
	}

	@Override
	protected void handleMessage(Object msg) {
		final ChatMessage chatMsg = (ChatMessage) msg;

		switch (chatMsg.getChatMode()) {
		case PUBLIC:

			break;
		case WHISPER:
			handleWhisper(chatMsg);
			break;

		case PARTY:
		case GUILD:
			// no op.
			break;
		default:
			LOG.warning("Message type not yet supported.");
			break;
		}
	}

	private void handleWhisper(ChatMessage chatMsg) {
		// Cant handle with no receiver name.
		if(chatMsg.getReceiverNickname() == null) {
			return;
		}
		
		final Account acc = accService.getOnlineAccountByName(chatMsg.getReceiverNickname());
		
		if(acc == null) {
			return;
		}
		
		final ChatMessage reply = ChatMessage.getEchoMessage(acc.getId(), chatMsg);
		responder.tell(reply, getSelf());
	}

}
