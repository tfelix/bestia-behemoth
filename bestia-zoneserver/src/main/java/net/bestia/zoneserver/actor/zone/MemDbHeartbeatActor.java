package net.bestia.zoneserver.actor.zone;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.Address;
import akka.actor.Cancellable;
import akka.cluster.Cluster;
import net.bestia.zoneserver.configuration.StaticConfigService;
import scala.concurrent.duration.Duration;

/**
 * This actor sends a heartbeat to the discovery system so the zone node is seen as alive.
 * If the heartbeat stops for too long the memory db will remove the node from
 * the active list.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class MemDbHeartbeatActor extends AbstractActor {

	public static final String NAME = "heartbeat";

	private static final String BEAT_MSG = "beat";
	public static final int HEARTBEAT_INTERVAL_S = 10;

	private final String serverName;

	private final Cancellable tick = getContext().getSystem().scheduler().schedule(
			Duration.create(500, TimeUnit.MILLISECONDS),
			Duration.create(HEARTBEAT_INTERVAL_S, TimeUnit.SECONDS),
			getSelf(), BEAT_MSG, getContext().dispatcher(), null);

	@Override
	public void postStop() {
		tick.cancel();
	}

	@Autowired
	public MemDbHeartbeatActor(
			StaticConfigService configService) {

		this.serverName = configService.getServerName();
		//this.discoveryService = Objects.requireNonNull(discoveryService);
	}

	/**
	 * Updates in a periodic manner the existence of this node.
	 */
	private void handleTick() {
		final Address clusterAddr = Cluster.get(context().system()).selfAddress();
		//discoveryService.addClusterNode(serverName, clusterAddr);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.matchEquals(BEAT_MSG, m -> {
					handleTick();
				})
				.build();
	}

}
