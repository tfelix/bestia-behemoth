package net.bestia.zoneserver.actor.zone;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.Member;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.server.AkkaCluster;

/**
 * Logs information about the whole behemoth cluster.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class ZoneClusterListenerActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final Cluster cluster = Cluster.get(getContext().system());

	private final List<Member> webserverMember = new ArrayList<>();

	/**
	 * Subscribe to cluster changes
	 */
	@Override
	public void preStart() {
		cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(),
				MemberEvent.class, UnreachableMember.class);
	}

	/**
	 * re-subscribe when restart
	 */
	@Override
	public void postStop() {
		cluster.unsubscribe(getSelf());
	}

	@Override
	public void onReceive(Object message) {
		if (message instanceof MemberUp) {
			final MemberUp mUp = (MemberUp) message;

			if (mUp.member().hasRole(AkkaCluster.ROLE_WEB)) {
				LOG.info("Webserver is up: {}", mUp.member());
				webserverMember.add(mUp.member());
			} else {
				LOG.info("Zoneserver is up: {}", mUp.member());
			}

		} else if (message instanceof UnreachableMember) {

			final UnreachableMember mUnreachable = (UnreachableMember) message;
			LOG.warning("Member detected as unreachable: {}", mUnreachable.member());
			
			// If its a webserver we can automatically down it. Since they will
			// terminate upon disconnection.
			if(mUnreachable.member().hasRole(AkkaCluster.ROLE_WEB)) {
				LOG.warning("Member has role WEBSERVER downing it.");
				cluster.down(mUnreachable.member().address());
			} else {
				// TODO Das hier ist temporär und muss noch in DowningProvider ausgelagert werden.
				LOG.warning("TEMP TEST OF DOWNING");
				cluster.down(mUnreachable.member().address());
			}

		} else if (message instanceof MemberRemoved) {

			final MemberRemoved mRemoved = (MemberRemoved) message;
			LOG.debug("Member is removed: {}", mRemoved.member());

			if (!mRemoved.member().hasRole(AkkaCluster.ROLE_WEB)) {
				// TODO we must terminate all user connections from this webserver.
				LOG.warning("Must terminate all connections from this node.");
				return;
			} else {
				
			}

			LOG.warning("Webserver is removed: {}", mRemoved.member());

		} else if (message instanceof MemberEvent) {
			// ignore
		} else {
			unhandled(message);
		}

	}
}
