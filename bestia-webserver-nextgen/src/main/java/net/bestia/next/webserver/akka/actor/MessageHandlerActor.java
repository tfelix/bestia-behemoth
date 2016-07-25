package net.bestia.next.webserver.akka.actor;

import java.util.Objects;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import net.bestia.next.messages.AccountMessage;
import net.bestia.next.messages.ClientMessage;

public class MessageHandlerActor extends UntypedActor {

	private final WebSocketSession session;
	private final ObjectMapper mapper;

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
		
		if(message instanceof ClientMessage) {
			// Send back to the client.			
			final String payload = mapper.writeValueAsString(message);	
			session.sendMessage(new TextMessage(payload));
			
		} else if(message instanceof AccountMessage) {
			// Send message into the zone server cluster.
			// TODO
		 	
		} else {
			unhandled(message);
		}
	}

}
