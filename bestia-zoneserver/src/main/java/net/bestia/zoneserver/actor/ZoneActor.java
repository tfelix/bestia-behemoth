package net.bestia.zoneserver.actor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Deploy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.Message;
import net.bestia.messages.inventory.InventoryListRequestMessage;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.actor.inventory.InventoryListActor;
import net.bestia.zoneserver.actor.login.LoginActor;
import net.bestia.zoneserver.actor.system.InitActor;

/**
 * Central actor for handling zone related messages. This actor will redirect
 * the various massages to the different actors and build up the initial needed
 * actor tree.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ZoneActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	// private final ActorRef clientResponseActor;
	private final ActorRef loginActor;
	private final ActorRef inventoryListActor;

	public ZoneActor(BestiaActorContext ctx) {

		final ActorSystem system = getContext().system();

		// Setup the init actor singelton for creation of the system.
		final ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
		system.actorOf(ClusterSingletonManager.props(InitActor.props(), PoisonPill.getInstance(), settings),
				InitActor.NAME);

		loginActor = getContext().actorOf(LoginActor.props(ctx), "login");
		inventoryListActor = getContext().actorOf(InventoryListActor.props(ctx), "inventory");

		// Setup the mediator.
		final ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();
		mediator.tell(new DistributedPubSubMediator.Subscribe(AkkaCluster.CLUSTER_PUBSUB_TOPIC, getSelf()), getSelf());

		// Get the reference to the init singelton.
		final ClusterSingletonProxySettings proxySettings = ClusterSingletonProxySettings.create(system);
		system.actorOf(ClusterSingletonProxy.props("/user/consumer", proxySettings), "initProxy");
	}

	public static Props props(BestiaActorContext ctx) {
		return Props.create(ZoneActor.class, ctx).withDeploy(Deploy.local());
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (message instanceof DistributedPubSubMediator.SubscribeAck) {
			LOG.info("subscribing");
			return;
		}

		if (!(message instanceof Message)) {
			unhandled(message);
			LOG.warning("Zone received unknown message: {}", message);
			return;
		}

		final Message msg = (Message) message;

		switch (msg.getMessageId()) {
		case InventoryListRequestMessage.MESSAGE_ID:
			inventoryListActor.tell(msg, getSender());
			break;
		case LoginAuthMessage.MESSAGE_ID:
			loginActor.tell(msg, getSender());
			break;
		}

	}
}
