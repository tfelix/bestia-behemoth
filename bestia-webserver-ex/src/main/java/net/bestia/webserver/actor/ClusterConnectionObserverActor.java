package net.bestia.webserver.actor;

import java.util.HashSet;
import java.util.Set;

import akka.actor.AbstractActor;
import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.client.ClusterClient;
import akka.cluster.client.ClusterClientSettings;
import akka.cluster.client.ContactPointAdded;
import akka.cluster.client.ContactPointChange;
import akka.cluster.client.ContactPointRemoved;
import akka.cluster.client.ContactPoints;
import akka.cluster.client.SubscribeContactPoints;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.webserver.messages.ClusterConnectionStatus;
import net.bestia.webserver.messages.ClusterConnectionStatus.State;
import net.bestia.webserver.messages.RegisterObserver;

/**
 * This actor observes all connections towards the Bestia Behemoth cluster. It
 * allows registration of actors so they can subscribe to events regarding the
 * bestia cluster connection status.
 * 
 * @author Thomas Felix
 *
 */
public class ClusterConnectionObserverActor extends AbstractActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final Set<ActorPath> contacts = new HashSet<>();
	private final Set<ActorRef> observingActors = new HashSet<>();

	private ActorRef clusterConnection;

	private ClusterConnectionObserverActor(Set<ActorPath> initialContacts) {

		this.contacts.addAll(initialContacts);
	}

	/**
	 * Akka props helper method.
	 * 
	 * @param terminator
	 * @param config
	 * @return
	 */
	public static Props props(Set<ActorPath> initialContacts) {
		return Props.create(new Creator<ClusterConnectionObserverActor>() {
			private static final long serialVersionUID = 1L;

			public ClusterConnectionObserverActor create() throws Exception {
				return new ClusterConnectionObserverActor(initialContacts);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ContactPoints.class, msg -> {
					contacts.addAll(msg.getContactPoints());
					handleContactsChanged(null);
				})
				.match(ContactPointAdded.class, msg -> {
					contacts.add(msg.contactPoint());
					handleContactsChanged(msg);
				})
				.match(ContactPointRemoved.class, msg -> {
					contacts.remove(msg.contactPoint());
					handleContactsChanged(msg);
				})
				.match(Terminated.class, this::handleClusterTerminated)
				.match(RegisterObserver.class, this::handleObserverRegister)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		final ActorSystem system = getContext().getSystem();
		final ClusterClientSettings clientSettings = ClusterClientSettings.create(system).withInitialContacts(contacts);

		clusterConnection = system.actorOf(ClusterClient.props(clientSettings), "connection");
		getContext().watch(clusterConnection);

		clusterConnection.tell(SubscribeContactPoints.getInstance(), self());
	}
	
	private void handleObserverRegister(RegisterObserver observer) {
		LOG.debug("Registering new observer: {}", observer.getObserver());
		observingActors.add(observer.getObserver());
	}
	
	private void handleContactsChanged(ContactPointChange cpc) {
		final ClusterConnectionStatus msg = new ClusterConnectionStatus(State.CONNECTED, clusterConnection);
		observingActors.forEach(a -> a.tell(msg, getSelf()));
	}

	/**
	 * Notify all registered observer that the connection to the cluster has
	 * gone away.
	 * 
	 * @param t
	 */
	private void handleClusterTerminated(Terminated t) {

		if (!t.actor().equals(clusterConnection)) {
			return;
		}

		clusterConnection = null;
		final ClusterConnectionStatus msg = new ClusterConnectionStatus(State.DISCONNECTED);
		observingActors.forEach(a -> a.tell(msg, getSelf()));
	}
}
