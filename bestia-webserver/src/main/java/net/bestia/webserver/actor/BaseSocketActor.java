package net.bestia.webserver.actor;

import java.io.IOException;
import java.util.Objects;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.AccountMessage;

public abstract class BaseSocketActor extends AbstractActor {
	
	final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	protected final ActorRef uplink;
	protected final WebSocketSession session;
	protected final ObjectMapper mapper;

	public BaseSocketActor(ActorRef uplink, ObjectMapper mapper, WebSocketSession session) {

		this.uplink = Objects.requireNonNull(uplink);
		this.mapper = Objects.requireNonNull(mapper);
		this.session = Objects.requireNonNull(session);
	}

	protected void sendToClient(AccountMessage message) {
		// Send the payload to the client.
		try {
			final String payload = mapper.writeValueAsString(message);
			LOG.debug("Server sending: {}.", payload);
			session.sendMessage(new TextMessage(payload));
		} catch (IOException | IllegalStateException e) {
			// Could not send to client.
			LOG.error("Could not send message: {}.", message.toString(), e);
			getContext().stop(getSelf());
		}
	}

}