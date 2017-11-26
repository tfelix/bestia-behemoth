package net.bestia.zoneserver.actor;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;

/**
 * This is a small proxy actor which can be used to break circular depdendencies
 * by actors. If all needed actors are created one can send a {@link ActorRef}
 * to this actor and all upcoming messages will be redirected towards this
 * actor.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ProxyHelperActor extends AbstractActor {

	private ActorRef ref = null;

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(ActorRef.class, this::setReceiver).matchAny(msg -> {
			if (ref != null) {
				ref.tell(msg, getSender());
			}
		}).build();
	}

	private void setReceiver(ActorRef ref) {
		this.ref = ref;
	}
}
