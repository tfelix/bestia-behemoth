package net.bestia.zoneserver.actor.chat;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.connection.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Handles public chat of the user and sends them to all entities which can
 * receive them.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class WhisperChatActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "whisper";

	private final AccountService accService;
	private final ActorRef msgHub;

	@Autowired
	public WhisperChatActor(AccountService accService) {

		this.accService = Objects.requireNonNull(accService);
		this.msgHub = SpringExtension.actorOf(getContext(), SendClientActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ChatMessage.class, this::handleWhisper)
				.build();
	}

	/**
	 * Handles an incoming whisper message.
	 */
	private void handleWhisper(ChatMessage chatMsg) {
		// Sanity check.
		if (chatMsg.getChatMode() != ChatMessage.Mode.WHISPER) {
			LOG.warning("Can not handle non whisper chat messages: {}.", chatMsg);
			unhandled(chatMsg);
			return;
		}

		// Cant handle with no receiver name.
		if (chatMsg.getReceiverNickname() == null) {
			return;
		}

		final Account acc = accService.getOnlineAccountByName(chatMsg.getReceiverNickname());

		if (acc == null) {
			LOG.debug("Whisper receiver {} not found.", chatMsg.getReceiverNickname());
			return;
		}

		final ChatMessage reply = chatMsg.createNewInstance(acc.getId());
		msgHub.tell(reply, getSelf());
	}
}
