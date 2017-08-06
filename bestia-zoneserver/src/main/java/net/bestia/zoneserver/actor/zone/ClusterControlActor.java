package net.bestia.zoneserver.actor.zone;

import java.io.Serializable;
import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.model.domain.MapParameter;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.map.MapGeneratorMasterActor;
import net.bestia.zoneserver.map.MapService;

/**
 * This is a cluster singelton actor. It centralized the control over the whole
 * bestia cluster. Upon receiving control messages it performs centralized
 * orchestration like generating a new map.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ClusterControlActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String START_MSG = "init.start";

	public static final class ControlMessage implements Serializable {

		private static final long serialVersionUID = 1L;

	}

	private boolean hasInitialized = false;

	private final MapService mapDataService;
	private final ActorRef mapGeneratorMaster;

	public ClusterControlActor(MapService mapDataService) {

		this.mapDataService = Objects.requireNonNull(mapDataService);
		this.mapGeneratorMaster = SpringExtension.actorOf(getContext(), MapGeneratorMasterActor.class);
	}

	@Override
	public void preStart() throws Exception {
		LOG.warning("INITGLOBAL STARTED");

		if (!mapDataService.isMapInitialized()) {
			initializeMap();
		}
	}

	private void initializeMap() {
		LOG.info("New map is generated.");
		MapParameter mapParams = MapParameter.fromAverageUserCount(1, "Terra");
		mapGeneratorMaster.tell(mapParams, getSelf());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.matchEquals(START_MSG, m -> this.startInit())
				.build();
	}

	private void startInit() {

		if (hasInitialized) {
			return;
		}

		hasInitialized = true;

		// Start the initialization process.
		LOG.info("Start the global server initialization...");
	}

	/**
	 * Checks if the bestia game was just shut down or if there is a new start
	 * which means world generation should begin.
	 */
	private void checkInitStatus() {

	}
}
