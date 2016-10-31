package net.bestia.zoneserver.actor.zone;

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
import net.bestia.messages.chat.ChatMessage;
import net.bestia.messages.internal.StartInitMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.inventory.InventoryActor;
import net.bestia.zoneserver.actor.login.LoginActor;
import net.bestia.zoneserver.actor.test.RoutingRootTest;
import net.bestia.zoneserver.actor.zone.InitLocalActor.LocalInitDone;

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
	
	private ActorRef testActor;

	public ZoneActor() {

		final ActorSystem system = getContext().system();
		
		createActor(LoginActor.class, "login");
		createActor(InventoryActor.class, InventoryActor.NAME);

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

		// Do the local init like loading scripts. When this is finished we can
		// register ourselves with the messaging system.
		ActorRef localInitActor = createActor(InitLocalActor.class, "localInit");
		localInitActor.tell(new StartInitMessage(), getSelf());

		// Some utility actors.
		createActor(ClusterStatusListenerActor.class, "clusterStatusListener");

		// This is for testing.
		// === Test Actor ===
		testActor = createActor(RoutingRootTest.class, "test");
		final ChatMessage chat = new ChatMessage();
		chat.setChatMessageId(1);
		chat.setChatMode(ChatMessage.Mode.PUBLIC);
		chat.setSenderNickname("bla");
		chat.setText("Hello World");
		testActor.tell(chat, getSelf());
	}


	@Override
	protected void handleMessage(Object msg) {
		if (msg instanceof DistributedPubSubMediator.SubscribeAck) {
			LOG.info("subscribing");
			return;
		}

		if (msg instanceof LocalInitDone) {

			// If we have finished loading setup the mediator to receive pub sub messages.
			final ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();
			mediator.tell(new DistributedPubSubMediator.Subscribe(AkkaCluster.CLUSTER_PUBSUB_TOPIC, getSelf()),
					getSelf());
		}
	}
}
