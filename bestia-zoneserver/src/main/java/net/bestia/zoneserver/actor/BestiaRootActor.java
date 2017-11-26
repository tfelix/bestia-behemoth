package net.bestia.zoneserver.actor;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.client.ClusterClientReceptionist;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.server.EntryActorNames;
import net.bestia.zoneserver.actor.connection.ClientConnectionActor;
import net.bestia.zoneserver.actor.entity.EntityActor;
import net.bestia.zoneserver.actor.rest.ChangePasswordActor;
import net.bestia.zoneserver.actor.rest.CheckUsernameDataActor;
import net.bestia.zoneserver.actor.rest.RequestLoginActor;
import net.bestia.zoneserver.actor.rest.RequestServerStatusActor;
import net.bestia.zoneserver.actor.zone.ClientMessageHandlerActor;
import net.bestia.zoneserver.actor.zone.ClusterControlActor;
import net.bestia.zoneserver.actor.zone.MessageRouterActor;
import net.bestia.zoneserver.actor.zone.MessageRouterActor.SetMessageRoutes;
import net.bestia.zoneserver.actor.zone.WebIngestActor;
import net.bestia.zoneserver.actor.zone.ZoneClusterListenerActor;
import net.bestia.zoneserver.script.ScriptService;

/**
 * Central root actor of the bestia zone hierarchy.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class BestiaRootActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public final static String NAME = "bestia";

	private ActorRef clientMessageHandler;
	private final ActorRef messageHub;
	
	private final ScriptService scriptService;
	private final ZoneMessageApi akkaMsgApi;
	

	@Autowired
	public BestiaRootActor(ScriptService scriptService, ZoneMessageApi akkaMsgApi) {

		this.scriptService = Objects.requireNonNull(scriptService);
		this.akkaMsgApi = Objects.requireNonNull(akkaMsgApi);
		
		this.messageHub = SpringExtension.actorOf(getContext(), MessageRouterActor.class);
	}

	@Override
	public void preStart() throws Exception {
		
		clientMessageHandler = SpringExtension.actorOf(getContext(), ClientMessageHandlerActor.class, messageHub);
		
		registerShardedActors();
		
		registerSingeltons();
		
		// Setup the messaging system.
		akkaMsgApi.setMessageEntry(messageHub);
		
		// System actors.
		SpringExtension.actorOf(getContext(), ZoneClusterListenerActor.class);

		// Maintenance actors.
		// Noch nicht migriert.
		// akkaApi.startActor(MapGeneratorMasterActor.class);
		// akkaApi.startActor(MapGeneratorClientActor.class);

		// === Web/REST actors ===
		SpringExtension.actorOf(getContext(), CheckUsernameDataActor.class);
		SpringExtension.actorOf(getContext(), ChangePasswordActor.class);
		SpringExtension.actorOf(getContext(), RequestLoginActor.class);
		SpringExtension.actorOf(getContext(), RequestServerStatusActor.class);
		
		// Trigger the startup script.
		scriptService.callScript("startup");
		
		final ActorRef ingest = SpringExtension.actorOf(getContext(), WebIngestActor.class);
		ClusterClientReceptionist.get(getContext().getSystem()).registerService(ingest);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().build();
	}

	private void registerSingeltons() {
		// Setup the init actor singelton for creation of the system.
		LOG.info("Starting the global init singeltons.");
		
		final ActorSystem system = getContext().system();
		
		final ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
		final Props globalInitProps = SpringExtension.getSpringProps(system, ClusterControlActor.class);
		Props clusterProbs = ClusterSingletonManager.props(globalInitProps, PoisonPill.getInstance(), settings);
		system.actorOf(clusterProbs, "globalInit");
	}
	
	private void registerShardedActors() {
		LOG.info("Register the sharded actor.");

		final ActorSystem system = getContext().system();

		
		final ClusterShardingSettings settings = ClusterShardingSettings.create(system);
		final ClusterSharding sharding = ClusterSharding.get(system);
		
		// Entity sharding.
		final Props entityProps = SpringExtension.getSpringProps(system, EntityActor.class);
		final EntityShardMessageExtractor entityExtractor = new EntityShardMessageExtractor();
		final ActorRef entities = sharding.start(EntryActorNames.SHARD_ENTITY, entityProps, settings, entityExtractor);

		// Client connection sharding.
		final Props connectionProps = SpringExtension.getSpringProps(system, ClientConnectionActor.class, clientMessageHandler);
		final ConnectionShardMessageExtractor connectionExtractor = new ConnectionShardMessageExtractor();
		final ActorRef clients = sharding.start(EntryActorNames.SHARD_CONNECTION, connectionProps, settings, connectionExtractor);
		
		final SetMessageRoutes setMsg = new SetMessageRoutes(entities, clients);
		messageHub.tell(setMsg, getSelf());
	}
}
