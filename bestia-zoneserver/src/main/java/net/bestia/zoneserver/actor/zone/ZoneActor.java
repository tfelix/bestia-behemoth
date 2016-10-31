package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
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
import net.bestia.messages.MessageId;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.messages.internal.StartInitMessage;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.SpringExtension.SpringExt;
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
public class ZoneActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final ActorRef loginActor;
	private ActorRef localInitActor;
	
	private ActorRef testActor;

	public ZoneActor() {

		final ActorSystem system = getContext().system();

		final SpringExt springExt = SpringExtension.Provider.get(getContext().system());
		final Props loginProps = springExt.props(LoginActor.class);
		loginActor = getContext().actorOf(loginProps, "login");

		// Setup the init actor singelton for creation of the system.
		final ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(system);
		final Props globalInitProps = springExt.props(InitGlobalActor.class);
		Props clusterProbs = ClusterSingletonManager.props(globalInitProps, PoisonPill.getInstance(), settings);
		system.actorOf(clusterProbs, "globalInit");

		// Try to do the global init if it has not been done before.
		final ClusterSingletonProxySettings proxySettings = ClusterSingletonProxySettings.create(system);
		clusterProbs = ClusterSingletonProxy.props("/user/globalInit", proxySettings);
		final ActorRef initProxy = system.actorOf(clusterProbs, "globalInitProxy");
		initProxy.tell(new StartInitMessage(), getSelf());

		// Do the local init like loading scripts. When this is finished we can
		// register ourselves with the messaging system.
		final Props initProps = springExt.props(InitLocalActor.class);
		localInitActor = getContext().actorOf(initProps, "init");
		localInitActor.tell(new StartInitMessage(), getSelf());

		// Some utility actors.
		Props props = springExt.props(ClusterStatusListenerActor.class);
		getContext().actorOf(props, "clusterStatusListener");
		
		// This is for testing.
		// === Test Actor ===
		final Props testProps = springExt.props(RoutingRootTest.class);
		testActor = getContext().actorOf(testProps, "test");
		final ChatMessage chat = new ChatMessage();
		chat.setChatMessageId(1);
		chat.setChatMode(ChatMessage.Mode.PUBLIC);
		chat.setSenderNickname("bla");
		chat.setText("Hello World");
		testActor.tell(chat, getSelf());
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

		if (!(message instanceof MessageId)) {
			unhandled(message);
			LOG.warning("Zone received unknown message: {}", message);
			return;
		}

		final MessageId msg = (MessageId) message;

		switch (msg.getMessageId()) {
		case LoginAuthMessage.MESSAGE_ID:
			loginActor.tell(msg, getSender());
			break;
		}

	}
}
