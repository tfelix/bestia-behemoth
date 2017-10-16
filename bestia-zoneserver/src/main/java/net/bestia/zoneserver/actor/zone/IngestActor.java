package net.bestia.zoneserver.actor.zone;

import akka.actor.AbstractActor;

public class IngestActor extends AbstractActor {

	@Override
	public Receive createReceive() {
		return receiveBuilder().matchAny(s -> {
			System.out.println("Received: " + s.toString());
		}).build();
	}

}
