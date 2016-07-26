package net.bestia.zoneserver.actor;

import akka.actor.UntypedActor;
import net.bestia.messages.PingMessage;
import net.bestia.messages.PongMessage;

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
