package net.bestia.messages;

import java.io.Serializable;
import java.util.Objects;

import akka.actor.ActorRef;

public class ResponderEnvelope implements Serializable {
	
	private static final long serialVersionUID = 1L;
	private final ActorRef sender;
	private final Message message;
	
	public ResponderEnvelope(ActorRef sender, Message message) {
		
		this.sender = Objects.requireNonNull(sender);
		this.message = Objects.requireNonNull(message);
	}
	
	public Message getMessage() {
		return message;
	}
	
	public ActorRef getSender() {
		return sender;
	}
	
	public String getMessageId() {
		return message.getMessageId();
	}

}
