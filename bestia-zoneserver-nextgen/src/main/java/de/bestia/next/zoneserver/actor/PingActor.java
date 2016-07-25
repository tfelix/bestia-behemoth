package de.bestia.next.zoneserver.actor;

import akka.actor.UntypedActor;
import de.bestia.next.zoneserver.message.PingMessage;
import de.bestia.next.zoneserver.message.PongMessage;

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
