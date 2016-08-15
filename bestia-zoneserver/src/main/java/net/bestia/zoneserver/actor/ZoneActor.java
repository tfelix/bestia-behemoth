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
import net.bestia.messages.system.StartInitMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.actor.inventory.InventoryListActor;
import net.bestia.zoneserver.actor.login.LoginActor;
import net.bestia.zoneserver.actor.system.InitGlobalActor;
import net.bestia.zoneserver.actor.system.InitLocalActor;
import net.bestia.zoneserver.actor.system.InitLocalActor.LocalInitDone;

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

	private ActorRef localInitActor;

	public ZoneActor(BestiaActorContext ctx) {

		final ActorSystem system = getContext().system();

		// Setup the init actor singelton for creation of the system.
		final ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
		system.actorOf(ClusterSingletonManager.props(InitGlobalActor.props(), PoisonPill.getInstance(), settings),
				InitGlobalActor.NAME);

		loginActor = getContext().actorOf(LoginActor.props(ctx), "login");
		inventoryListActor = getContext().actorOf(InventoryListActor.props(ctx), "inventory");

		// Try to do the global init if it has not been done before.
		final ClusterSingletonProxySettings proxySettings = ClusterSingletonProxySettings.create(system);
		final ActorRef initProxy = system.actorOf(ClusterSingletonProxy.props("/user/consumer", proxySettings),
				"initProxy");

		initProxy.tell(new StartInitMessage(), getSelf());

		// Do the local init.
		localInitActor = getContext().actorOf(InitLocalActor.props(), "init");
		localInitActor.tell(new StartInitMessage(), getSelf());
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

		if (message instanceof LocalInitDone) {

			localInitActor = null;

			// If we have finished loading setup the mediator to receive pub sub
			// messages.
			final ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();
			mediator.tell(new DistributedPubSubMediator.Subscribe(AkkaCluster.CLUSTER_PUBSUB_TOPIC, getSelf()),
					getSelf());

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
