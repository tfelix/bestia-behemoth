package de.bestia.akka.actor;

import akka.actor.UntypedActor;
import de.bestia.akka.message.PingMessage;
import de.bestia.akka.message.PongMessage;

public class PingActor extends UntypedActor {

	@Override
	public void onReceive(Object message) throws Exception {
		if(!(message instanceof PingMessage)) {
			unhandled(message);
			return;
		}
		
		PingMessage pingMsg = (PingMessage) message;
		
		getSender().tell(new PongMessage(), getSelf());
	}

}
