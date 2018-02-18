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
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.actor.zone.SendClientsInRangeActor;
import net.bestia.zoneserver.actor.zone.SendEntityActor;
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

	private final ScriptService scriptService;
	private final ZoneMessageApi messageApi;

	private ActorSystem system;
	private ClusterSharding sharding;
	private ClusterShardingSettings settings;

	@Autowired
	public BestiaRootActor(ScriptService scriptService, ZoneMessageApi messageApi) {

		this.scriptService = Objects.requireNonNull(scriptService);
		this.messageApi = Objects.requireNonNull(messageApi);

		this.system = getContext().system();
		this.settings = ClusterShardingSettings.create(system);
		this.sharding = ClusterSharding.get(system);
	}

	@Override
	public void preStart() throws Exception {

		registerSingeltons();
		ActorRef helperProxy = registerSharding();

		clientMessageHandler = SpringExtension.actorOf(getContext(), ClientMessageHandlerActor.class);

		helperProxy.tell(clientMessageHandler, getSelf());

		// Setup the messaging system. This is also needed because the message
		// api is created bevor the registering takes place
		// thus to break the circular dependency this trick is needed.
		messageApi.setReceivingActor(SpringExtension.actorOf(getContext(), SendClientActor.class), 
				SpringExtension.actorOf(getContext(), SendClientsInRangeActor.class), 
				SpringExtension.actorOf(getContext(), SendEntityActor.class));

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

		// Register the cluster client receptionist for receiving messages.
		final ActorRef ingest = SpringExtension.actorOf(getContext(), WebIngestActor.class);
		ClusterClientReceptionist.get(getContext().getSystem()).registerService(ingest);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().build();
	}

	private ActorRef registerSharding() {
		// Setup the init actor singelton for creation of the system.
		LOG.info("Starting the sharding.");

		// Entity sharding.
		final Props entityProps = SpringExtension.getSpringProps(system, EntityActor.class);
		final EntityShardMessageExtractor entityExtractor = new EntityShardMessageExtractor();
		sharding.start(EntryActorNames.INSTANCE.getSHARD_ENTITY(), entityProps, settings, entityExtractor);

		// Client connection sharding.
		ActorRef helperProxy = SpringExtension.actorOf(getContext(), ProxyHelperActor.class);
		final Props connectionProps = SpringExtension.getSpringProps(system, ClientConnectionActor.class,
				helperProxy);
		final ConnectionShardMessageExtractor connectionExtractor = new ConnectionShardMessageExtractor();
		sharding.start(EntryActorNames.INSTANCE.getSHARD_CONNECTION(), connectionProps, settings, connectionExtractor);

		LOG.info("Started the sharding.");
		return helperProxy;
	}

	private void registerSingeltons() {
		// Setup the init actor singelton for creation of the system.
		LOG.info("Starting the global init singeltons.");

		final ActorSystem system = getContext().system();

		final ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
		final Props globalInitProps = SpringExtension.getSpringProps(system, ClusterControlActor.class);
		Props clusterProbs = ClusterSingletonManager.props(globalInitProps, PoisonPill.getInstance(), settings);
		system.actorOf(clusterProbs, "globalInit");

		LOG.info("Started the global init singeltons.");
	}
}
