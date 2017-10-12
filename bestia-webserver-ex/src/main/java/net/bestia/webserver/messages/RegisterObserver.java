package net.bestia.webserver.messages;

import java.util.Objects;

import akka.actor.ActorRef;

public class RegisterObserver {
	
	private final ActorRef observer;
	
	public RegisterObserver(ActorRef observer) {
		
		this.observer = Objects.requireNonNull(observer);
	}
	
	public ActorRef getObserver() {
		return observer;
	}
}
