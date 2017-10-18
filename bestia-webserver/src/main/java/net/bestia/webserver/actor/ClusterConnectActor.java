package net.bestia.webserver.actor;

import java.util.List;
import java.util.concurrent.TimeUnit;

import akka.actor.AbstractActor;
import akka.actor.Address;
import akka.actor.Cancellable;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberUp;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import scala.concurrent.duration.Duration;

/**
 * This actor tries to re-establish connection with the bestia cluster. After
 * the conenction has been established it will terminate.
 * 
 * @author Thomas Felix
 *
 */
public class ClusterConnectActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final Cluster cluster = Cluster.get(getContext().system());
	private final static String TICK_MSG = "tryConnect";
	public final static String NAME = "cluster";

	private final Cancellable tick = getContext().getSystem().scheduler().schedule(
			Duration.create(1, TimeUnit.SECONDS),
			Duration.create(10, TimeUnit.SECONDS),
			getSelf(), TICK_MSG, getContext().dispatcher(), null);

	public ClusterConnectActor() {
		// no op.
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
				.match(MemberUp.class, this::handleMemberUp)
				.matchEquals(TICK_MSG, msg -> connect())
				.build();
	}

	/**
	 * Checks if we are now connected.
	 * 
	 * @param msg
	 */
	private void handleMemberUp(MemberUp msg) {
		// We are connected. Can now terminate.
		context().stop(getSelf());
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

		cluster.joinSeedNodes(seedNodes);
	}

	// subscribe to cluster changes
	@Override
	public void preStart() {
		cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), MemberUp.class);
	}

	// un-subscribe when restart
	@Override
	public void postStop() {
		cluster.unsubscribe(getSelf());
		tick.cancel();
	}
}
