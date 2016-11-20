package net.bestia.webserver.actor;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.UntypedActor;
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

/**
 * This listens to the state of the cluster and plays an important role in
 * deciding if there is a cluster connection available. If connections to all
 * bestia cluster are lost we must end this program and disconnect all clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class WebClusterListenerActor extends UntypedActor {
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final Cluster cluster = Cluster.get(getContext().system());

	private final Set<Member> zoneMember = new HashSet<>();
	private final ActorSystemTerminator teminator;
	
	/**
	 * Ctor.
	 * 
	 * @param session
	 *            The websocket session attached to this connection.
	 * @param mapper
	 *            An jackson json mapper.
	 */
	public WebClusterListenerActor(ActorSystemTerminator terminator) {

		this.teminator = Objects.requireNonNull(terminator, "Terminator can not be null.");

	}
	
	/**
	 * Akka props helper method.
	 * 
	 * @param session
	 * @param mapper
	 * @return
	 */
	public static Props props(ActorSystemTerminator terminator) {
		return Props.create(new Creator<WebClusterListenerActor>() {
			private static final long serialVersionUID = 1L;

			public WebClusterListenerActor create() throws Exception {
				return new WebClusterListenerActor(terminator);
			}
		}).withDeploy(Deploy.local());
	}

	// subscribe to cluster changes
	@Override
	public void preStart() {
		cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), 
				MemberEvent.class, UnreachableMember.class);
	}

	// re-subscribe when restart
	@Override
	public void postStop() {
		cluster.unsubscribe(getSelf());
	}

	@Override
	public void onReceive(Object message) {
		if (message instanceof MemberUp) {
			MemberUp mUp = (MemberUp) message;
			
			if(mUp.member().address().equals(getSelf().path().address())) {
				// Ignore ourself.
				return;
			}

			if (mUp.member().hasRole(AkkaCluster.ROLE_ZONE)) {
				LOG.info("Zone member is up: {}", mUp.member());
				zoneMember.add(mUp.member());
			}
		} else if (message instanceof UnreachableMember) {
			UnreachableMember mUnreachable = (UnreachableMember) message;
			LOG.info("Member detected as unreachable: {}", mUnreachable.member());
			removeCheckedZone(mUnreachable.member());

		} else if (message instanceof MemberRemoved) {
			MemberRemoved mRemoved = (MemberRemoved) message;
			LOG.info("Member is Removed: {}", mRemoved.member());
			removeCheckedZone(mRemoved.member());

		} else if (message instanceof MemberEvent) {
			// ignore

		} else {
			unhandled(message);
		}
	}

	/**
	 * Checks member, if its a zone it will removed from the remaining list of
	 * clusters. If the last zone has been removed, terminate the system.
	 * 
	 * @param member
	 */
	private void removeCheckedZone(Member member) {
		if(member.hasRole(AkkaCluster.ROLE_ZONE)) {
			zoneMember.remove(member);
		}
		
		if(zoneMember.size() == 0) {
			// Terminate.
			LOG.info("Last zone member was removed from cluster. Terminating.");
			teminator.run();
		}
	}
}
