package net.bestia.webserver.actor;

import java.util.Objects;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.client.ClusterClient;
import akka.cluster.client.ClusterClientSettings;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.webserver.messages.web.ClusterConnectionTerminated;
import net.bestia.webserver.service.ConfigurationService;

/**
 * This actor tries to re-establish connection with the bestia cluster. If the
 * connection is terminated it will start to re-establish the connection
 * automatically. If the connection goes away it will send a error message to
 * the
 * 
 * @author Thomas Felix
 *
 */
public class ClusterConnectActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private ActorRef uplink;
	private final ConfigurationService config;

	public ClusterConnectActor(ConfigurationService config) {

		this.config = Objects.requireNonNull(config);
		createUplink();
	}
	
	private void createUplink() {
		LOG.info("Connecting to cluster.");
		
		final ClusterClientSettings settings = ClusterClientSettings.create(getContext().getSystem());
		uplink = getContext().actorOf(ClusterClient.props(settings), "uplink");
		getContext().watch(uplink);
		
		// TODO das hier erst auf true setzen wenn die connection besteht.
		config.setConnectedToCluster(true);
	}

	/**
	 * Akka props helper method.
	 * 
	 * @param terminator
	 * @param config
	 * @return
	 */
	public static Props props(ConfigurationService config) {
		return Props.create(new Creator<ClusterConnectActor>() {
			private static final long serialVersionUID = 1L;

			public ClusterConnectActor create() throws Exception {
				return new ClusterConnectActor(config);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Terminated.class, this::handleConnectionLost)
				.matchAny(this::handleAllMessages)
				.build();
	}

	private void handleAllMessages(Object msg) {
		uplink.tell(new ClusterClient.Send("/user/bestia/ingest", msg, true), getSender());
	}
	
	/**
	 * Notifiy parent about connection lost.
	 * 
	 * @param msg
	 */
	private void handleConnectionLost(Terminated msg) {
		LOG.info("Connection to cluster lost.");
		getContext().parent().tell(new ClusterConnectionTerminated(), getSelf());
		createUplink();
	}
}
