package de.bestia.next.zoneserver.actor;

import akka.actor.UntypedActor;
import net.bestia.next.messages.PingMessage;
import net.bestia.next.messages.PongMessage;

public class PingActor extends UntypedActor {

	@Override
	public void onReceive(Object message) throws Exception {
		if(!(message instanceof PingMessage)) {
			unhandled(message);
			return;
		}
		
		getSender().tell(new PongMessage(), getSelf());
	}

}
