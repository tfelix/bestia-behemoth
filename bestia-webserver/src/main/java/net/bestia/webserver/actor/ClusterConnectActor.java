package net.bestia.webserver.actor;

import java.util.List;
import java.util.concurrent.TimeUnit;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Cancellable;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.client.ClusterClient;
import akka.cluster.client.ClusterClientSettings;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.webserver.ClusterConnectionTerminated;
import scala.concurrent.duration.Duration;

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

	private final ActorRef uplink;

	public ClusterConnectActor() {

		final ClusterClientSettings settings = ClusterClientSettings.create(getContext().getSystem());
		uplink = getContext().actorOf(ClusterClient.props(settings), "uplink");

		getContext().watch(uplink);
	}

	/**
	 * Akka props helper method.
	 * 
	 * @param terminator
	 * @param config
	 * @return
	 */
	public static Props props() {
		return Props.create(new Creator<ClusterConnectActor>() {
			private static final long serialVersionUID = 1L;

			public ClusterConnectActor create() throws Exception {
				return new ClusterConnectActor();
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Terminated.class, this::handleConnectionLost)
				.build();
	}

	/**
	 * Notifiy parent about connection lost.
	 * 
	 * @param msg
	 */
	private void handleConnectionLost(Terminated msg) {
		getContext().parent().tell(new ClusterConnectionTerminated(), getSelf());
	}

	/**
	 * Tries to connect to the cluster.
	 */
	public void connect() {

		final List<Address> seedNodes = null;

		if (seedNodes == null || seedNodes.isEmpty()) {
			LOG.error("No seed cluster nodes found. Can not join the system. Retry.");
			return;
		}

		LOG.info("Got seed nodes: {}.", seedNodes);
		LOG.info("Attempting to joing the bestia cluster...");

	}
}
