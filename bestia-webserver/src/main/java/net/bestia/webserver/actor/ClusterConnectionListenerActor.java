package net.bestia.webserver.actor;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.hazelcast.core.HazelcastInstance;

import akka.actor.AbstractActor;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.Member;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.server.AkkaCluster;
import net.bestia.webserver.service.ConfigurationService;

/**
 * This listens to the state of the cluster and plays an important role in
 * deciding if there is a cluster connection available. If connections to all
 * bestia cluster are lost we must end this program and disconnect all clients.
 * 
 * @author Thomas Felix
 *
 */
public class ClusterConnectionListenerActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final Cluster cluster = Cluster.get(getContext().system());

	private final Set<Member> zoneMember = new HashSet<>();
	private final ConfigurationService config;
	private final HazelcastInstance hzClient;

	/**
	 * Ctor.
	 * 
	 * @param session
	 *            The websocket session attached to this connection.
	 * @param mapper
	 *            An jackson json mapper.
	 */
	public ClusterConnectionListenerActor(ConfigurationService config, HazelcastInstance hzClient) {

		this.config = Objects.requireNonNull(config);
		this.hzClient = Objects.requireNonNull(hzClient);
	}

	/**
	 * Akka props helper method.
	 * 
	 * @param terminator
	 * @param config
	 * @return
	 */
	public static Props props(ConfigurationService config, HazelcastInstance hzClient) {
		return Props.create(new Creator<ClusterConnectionListenerActor>() {
			private static final long serialVersionUID = 1L;

			public ClusterConnectionListenerActor create() throws Exception {
				return new ClusterConnectionListenerActor(config, hzClient);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(MemberUp.class, this::onMemberUp)
				.match(UnreachableMember.class, this::onMemberUnreachable)
				.match(MemberRemoved.class, this::onMemberDown)
				.build();
	}

	private void onMemberUnreachable(UnreachableMember msg) {
		LOG.info("Member is unreachable: {}", msg.member());
		removeCheckedZone(msg.member());
		checkSelfDisconnect(msg.member());
	}

	private void onMemberDown(MemberRemoved msg) {
		LOG.info("Member is removed: {}", msg.member());
		removeCheckedZone(msg.member());
		checkSelfDisconnect(msg.member());
	}

	private void onMemberUp(MemberUp msg) {
		if (isSelf(msg.member())) {
			LOG.info("Webserver himself connected to the cluster.");
			config.setConnectedToCluster(true);
		}

		if (msg.member().hasRole(AkkaCluster.ROLE_ZONE)) {
			LOG.info("Zone member is up: {}", msg.member());
			zoneMember.add(msg.member());
		}
	}

	private void checkSelfDisconnect(Member member) {
		if (isSelf(member)) {
			LOG.info("Webserver himself was disconnected from the cluster.");
			config.setConnectedToCluster(false);
		}
	}

	private boolean isSelf(Member member) {
		return member.address().equals(getSelf().path().address());
	}

	// subscribe to cluster changes
	@Override
	public void preStart() {
		cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), MemberEvent.class);
	}

	// un-subscribe when restart
	@Override
	public void postStop() {
		cluster.unsubscribe(getSelf());
	}

	/**
	 * Checks member, if its a zone it will removed from the remaining list of
	 * clusters. If the last zone has been removed, terminate the system.
	 * 
	 * @param member
	 */
	private void removeCheckedZone(Member member) {
		if (member.hasRole(AkkaCluster.ROLE_ZONE)) {
			zoneMember.remove(member);
		}

		if (zoneMember.size() == 0) {
			// Terminate.
			LOG.info("Last zone member was removed from cluster. Stop connections.");
			config.setConnectedToCluster(false);
			
			// Restart connection actor.
			final Props clusterConnectProps = ClusterConnectActor.props(hzClient);
			getContext().system().actorOf(clusterConnectProps, ClusterConnectActor.NAME);
		}
	}
}
