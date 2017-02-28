package net.bestia.zoneserver.actor.zone;

import java.util.Arrays;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.DoneMessage;
import net.bestia.messages.internal.StartInitMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.SpawnActorHelper;
import net.bestia.zoneserver.actor.battle.AttackPlayerUseActor;
import net.bestia.zoneserver.actor.bestia.ActivateBestiaActor;
import net.bestia.zoneserver.actor.bestia.BestiaInfoActor;
import net.bestia.zoneserver.actor.chat.ChatActor;
import net.bestia.zoneserver.actor.entity.EntitySpawnActor;
import net.bestia.zoneserver.actor.entity.EntityMoveActor;
import net.bestia.zoneserver.actor.entity.EntityInteractionRequestActor;
import net.bestia.zoneserver.actor.inventory.InventoryActor;
import net.bestia.zoneserver.actor.map.MapRequestChunkActor;
import net.bestia.zoneserver.actor.map.TilesetRequestActor;

/**
 * Central actor for handling zone related messages. This actor will redirect
 * the various massages to the different actors and build up the initial needed
 * actor tree.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component("ZoneActor")
@Scope("prototype")
public class ZoneActor extends BestiaRoutingActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "zoneRoot";

	public ZoneActor() {
		super(Arrays.asList(DoneMessage.class));

		final ActorSystem system = getContext().system();

		// === Login ===
		createActor(LoginActor.class);
		
		// === Map ===
		createActor(MapRequestChunkActor.class);
		createActor(TilesetRequestActor.class);
		
		// === Inventory ===
		createActor(InventoryActor.class);

		// === Bestias ===
		createActor(BestiaInfoActor.class);
		createActor(ActivateBestiaActor.class);
		createActor(EntitySpawnActor.class);
		
		// === Entities ===
		createActor(EntityInteractionRequestActor.class);
		createActor(EntityMoveActor.class);

		// === Chat ===
		createActor(ChatActor.class);
		
		// === Attacking ===
		createActor(AttackPlayerUseActor.class);
		
		// === Helper Actors
		createActor(EngineReadyActor.class);

		// === House keeping actors ===
		createActor(LogoutActor.class);
		createActor(PingPongActor.class);
		createActor(ZoneClusterListenerActor.class);
		
		// === DEVELOPMENT ===
		createActor(SpawnActorHelper.class);

		// Setup the init actor singelton for creation of the system.
		final ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
		final Props globalInitProps = getSpringProps(InitGlobalActor.class);
		Props clusterProbs = ClusterSingletonManager.props(globalInitProps, PoisonPill.getInstance(), settings);
		system.actorOf(clusterProbs, "globalInit");

		// Try to do the global init if it has not been done before.
		final ClusterSingletonProxySettings proxySettings = ClusterSingletonProxySettings.create(system);
		clusterProbs = ClusterSingletonProxy.props("/user/globalInit", proxySettings);
		final ActorRef initProxy = system.actorOf(clusterProbs, "globalInitProxy");
		initProxy.tell(new StartInitMessage(), getSelf());
		
		// Temporary register without init.
		final ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();
		mediator.tell(new DistributedPubSubMediator.Subscribe(
				AkkaCluster.CLUSTER_PUBSUB_TOPIC,
				getSelf()),
				getSelf());
	}

	@Override
	protected void handleMessage(Object msg) {
		// no op.
	}

	@Override
	protected void handleUnknownMessage(Object msg) {
		if (msg instanceof DistributedPubSubMediator.SubscribeAck) {
			LOG.info("Subscribed to the behemoth cluster.");
			return;
		}

		super.handleUnknownMessage(msg);
	}
}
