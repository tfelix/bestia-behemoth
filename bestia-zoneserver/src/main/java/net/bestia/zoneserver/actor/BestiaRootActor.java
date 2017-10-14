package net.bestia.zoneserver.actor;

import akka.actor.AbstractActor;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.zoneserver.actor.zone.IngestExActor;
import net.bestia.zoneserver.actor.zone.MemDbHeartbeatActor;
import net.bestia.zoneserver.actor.zone.SendActiveClientsActor;
import net.bestia.zoneserver.actor.zone.ZoneClusterListenerActor;

public class BestiaRootActor extends AbstractActor {

	public final static String NAME = "bestia";

	public BestiaRootActor() {

		SpringExtension.actorOf(getContext(), IngestExActor.class);
		SpringExtension.actorOf(getContext(), SendActiveClientsActor.class);

		// System actors.
		SpringExtension.actorOf(getContext(), ZoneClusterListenerActor.class);
		SpringExtension.actorOf(getContext(), MemDbHeartbeatActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityJsonMessage.class, this::sendActiveClients)
				.build();
	}

	private void sendActiveClients(EntityJsonMessage msg) {

	}
}
