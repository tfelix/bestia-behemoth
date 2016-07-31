package net.bestia.zoneserver.actor.system;

import akka.actor.UntypedActor;
import net.bestia.messages.system.PingMessage;
import net.bestia.messages.system.PongMessage;


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
