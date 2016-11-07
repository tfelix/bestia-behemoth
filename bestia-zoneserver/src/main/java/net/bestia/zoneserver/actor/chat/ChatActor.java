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
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;
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
	
	private final AccountZoneService accService;
	private final ActorRef responder;
	
	@Autowired
	public ChatActor(AccountZoneService accService) {
		super(Arrays.asList(ChatMessage.class));
		this.accService = Objects.requireNonNull(accService);
		this.responder = createActor(SendClientActor.class);
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
