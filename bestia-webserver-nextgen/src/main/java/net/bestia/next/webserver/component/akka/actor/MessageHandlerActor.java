package net.bestia.next.webserver.component.akka.actor;

import java.io.IOException;
import java.util.Objects;

import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.next.messages.AccountMessage;
import net.bestia.next.messages.LoginRequestMessage;

public class MessageHandlerActor extends UntypedActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final WebSocketSession session;
	private final ObjectMapper mapper;
	
	private boolean isAuthenticated = false;

	public MessageHandlerActor(WebSocketSession session, ObjectMapper mapper) {

		this.session = Objects.requireNonNull(session, "Session can not be null.");
		this.mapper = Objects.requireNonNull(mapper, "Mapper can not be null.");

	}

	public static Props props(WebSocketSession session, ObjectMapper mapper) {
		return Props.create(new Creator<MessageHandlerActor>() {
			private static final long serialVersionUID = 1L;

			public MessageHandlerActor create() throws Exception {
				return new MessageHandlerActor(session, mapper);
			}
		});
	}

	@Override
	public void onReceive(Object message) throws Exception {
		
		if(!(message instanceof String)) {
			unhandled(message);
			return;
		}
		
		final String payload = (String) message;
		
		// We only accept auth messages.
		if(!isAuthenticated) {
			try {
				final LoginRequestMessage loginReqMsg = mapper.readValue(payload, LoginRequestMessage.class);
				
				// TODO Send the LoginRequest to the cluster.
				
			} catch (IOException e) {
				// Wrong message.
				LOG.warning("Client {} send wrong first auth message. Payload was: {}.", session.getRemoteAddress(), payload);
				LOG.warning("Closing connection.");
				try {
					session.close();
				} catch (IOException e1) {
					// no op.
				}
				return;
			}
			
			return;
		}
		
		try {
			// Turn the text message into a bestia message.
			final AccountMessage msg = mapper.readValue(payload, AccountMessage.class);
		
			// TODO Send the Message to the cluster.
			
		} catch (IOException e) {
			LOG.warning("Malformed message. Client: {}. Payload: {}.", session.getRemoteAddress(), payload);
			LOG.warning("Closing connection.");
			try {
				session.close();
			} catch (IOException e1) {
				// no op.
			}
		}
		
		
	}

}
