package net.bestia.zoneserver.actor;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import net.bestia.zoneserver.actor.zone.IngestExActor;
import net.bestia.zoneserver.actor.zone.MemDbHeartbeatActor;
import net.bestia.zoneserver.actor.zone.SendActiveClientsActor;
import net.bestia.zoneserver.actor.zone.ZoneClusterListenerActor;

@Component
@Scope("prototype")
public class BestiaRootActor extends AbstractActor {

	public final static String NAME = "bestia";

	public BestiaRootActor() {

		SpringExtension.actorOf(getContext(), IngestExActor.class);
		//SpringExtension.actorOf(getContext(), SendActiveClientsActor.class);

		// System actors.
		SpringExtension.actorOf(getContext(), ZoneClusterListenerActor.class);
		SpringExtension.actorOf(getContext(), MemDbHeartbeatActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().build();
	}
}
