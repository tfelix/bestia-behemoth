package net.bestia.zoneserver.actor;

import akka.actor.*;
import akka.cluster.client.ClusterClientReceptionist;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import bestia.server.EntryActorNames;
import net.bestia.messages.*;
import net.bestia.zoneserver.actor.connection.ClientConnectionActor;
import net.bestia.zoneserver.actor.entity.EntityActor;
import net.bestia.zoneserver.actor.entity.SendEntityActor;
import net.bestia.zoneserver.actor.routing.PostmasterActor;
import net.bestia.zoneserver.actor.routing.RegisterEnvelopeMessage;
import net.bestia.zoneserver.actor.zone.*;
import net.bestia.zoneserver.script.ScriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Central root actor of the bestia zone hierarchy.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
public class BestiaRootActor extends AbstractActor {

  private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

  public final static String NAME = "bestia";

  private final ScriptService scriptService;
  private final MessageApi messageApi;

  private ActorSystem system;
  private ClusterSharding sharding;
  private ClusterShardingSettings settings;

  @Autowired
  public BestiaRootActor(ScriptService scriptService, MessageApi messageApi) {

    this.scriptService = Objects.requireNonNull(scriptService);
    this.messageApi = Objects.requireNonNull(messageApi);

    this.system = getContext().system();
    this.settings = ClusterShardingSettings.create(system);
    this.sharding = ClusterSharding.get(system);
  }

  @Override
  public void preStart() throws Exception {

    LOG.info("Bootstrapping Behemoth actor system.");
    final ActorRef postmaster = SpringExtension.actorOf(getContext(), PostmasterActor.class);

    // System actors.
    SpringExtension.actorOf(getContext(), ZoneClusterListenerActor.class);

    // Setup the messaging system. This is also needed because the message
    // api is created before the registering takes place
    // thus to break the circular dependency this trick is needed.
    messageApi.setPostmaster(postmaster);

    final ActorRef clientMessageActor = SpringExtension.actorOf(getContext(), ClientMessageActor.class, postmaster);

    registerSingeltons();
    registerSharding(postmaster);

    // === Web/REST actors ===
    // SpringExtension.actorOf(getContext(), CheckUsernameDataActor.class);
    // SpringExtension.actorOf(getContext(), ChangePasswordActor.class);
    // SpringExtension.actorOf(getContext(), RequestLoginActor.class);
    // SpringExtension.actorOf(getContext(), RequestServerStatusActor.class);

    // === Additional actors for message routing ===
    final ActorRef sendEntityActor = SpringExtension.actorOf(getContext(), SendEntityActor.class);
    final ActorRef sendClientActor = SpringExtension.actorOf(getContext(), SendClientActor.class);
    final ActorRef sendClientInRangeActor = SpringExtension.actorOf(getContext(), SendClientsInRangeActor.class);

    // === Register the postmaster connections ===
    final RegisterEnvelopeMessage regFromClientMessage = new RegisterEnvelopeMessage(ClientFromMessageEnvelope.class, clientMessageActor);
    postmaster.tell(regFromClientMessage, getSelf());
    final RegisterEnvelopeMessage regSendEntity = new RegisterEnvelopeMessage(EntityMessageEnvelope.class, sendEntityActor);
    postmaster.tell(regSendEntity, getSelf());
    final RegisterEnvelopeMessage regToClientMessage = new RegisterEnvelopeMessage(ClientToMessageEnvelope.class, sendClientActor);
    postmaster.tell(regToClientMessage, getSelf());
    final RegisterEnvelopeMessage regToClientsRangeMessage = new RegisterEnvelopeMessage(ClientsInRangeEnvelope.class, sendClientInRangeActor);
    postmaster.tell(regToClientsRangeMessage, getSelf());

    // Call the startup script.
    scriptService.callScriptMainFunction("startup");

    // Register the cluster client receptionist for receiving messages.
    final ActorRef ingest = SpringExtension.actorOf(getContext(), WebIngestActor.class, postmaster);
    ClusterClientReceptionist.get(getContext().getSystem()).registerService(ingest);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().build();
  }

  private void registerSharding(ActorRef postmaster) {
    // Setup the init actor singelton for creation of the system.
    LOG.info("Configuring sharding actors.");

    // Entity sharding.
    final Props entityProps = SpringExtension.getSpringProps(system, EntityActor.class);
    final EntityShardMessageExtractor entityExtractor = new EntityShardMessageExtractor();
    sharding.start(EntryActorNames.SHARD_ENTITY, entityProps, settings, entityExtractor);

    // Client connection sharding.
    final Props connectionProps = SpringExtension.getSpringProps(system, ClientConnectionActor.class,
            postmaster);
    final ConnectionShardMessageExtractor connectionExtractor = new ConnectionShardMessageExtractor();
    sharding.start(EntryActorNames.SHARD_CONNECTION, connectionProps, settings, connectionExtractor);

    LOG.info("Started the sharding actors.");
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
