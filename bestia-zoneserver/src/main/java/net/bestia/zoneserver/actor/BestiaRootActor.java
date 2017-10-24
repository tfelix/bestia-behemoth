package net.bestia.zoneserver.actor;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.client.ClusterClientReceptionist;
import net.bestia.zoneserver.actor.rest.ChangePasswordActor;
import net.bestia.zoneserver.actor.rest.CheckUsernameDataActor;
import net.bestia.zoneserver.actor.rest.RequestLoginActor;
import net.bestia.zoneserver.actor.rest.RequestServerStatusActor;
import net.bestia.zoneserver.actor.zone.MemDbHeartbeatActor;
import net.bestia.zoneserver.actor.zone.WebIngestActor;
import net.bestia.zoneserver.actor.zone.ZoneClusterListenerActor;

/**
 * Central root actor of the bestia zone hierarchy.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class BestiaRootActor extends AbstractActor {

	public final static String NAME = "bestia";


	public BestiaRootActor() {
		
		final ActorRef ingest = SpringExtension.actorOf(getContext(), WebIngestActor.class);
		ClusterClientReceptionist.get(getContext().getSystem()).registerService(ingest);

		// System actors.
		SpringExtension.actorOf(getContext(), ZoneClusterListenerActor.class);
		SpringExtension.actorOf(getContext(), MemDbHeartbeatActor.class);

		// Maintenance actors.
		// Noch nicht migriert.
		// akkaApi.startActor(MapGeneratorMasterActor.class);
		// akkaApi.startActor(MapGeneratorClientActor.class);
		
		// === Web/REST actors ===
		SpringExtension.actorOf(getContext(), CheckUsernameDataActor.class);
		SpringExtension.actorOf(getContext(), ChangePasswordActor.class);
		SpringExtension.actorOf(getContext(), RequestLoginActor.class);
		SpringExtension.actorOf(getContext(), RequestServerStatusActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().build();
	}

}
