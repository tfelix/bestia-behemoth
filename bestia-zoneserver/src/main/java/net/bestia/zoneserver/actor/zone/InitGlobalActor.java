package net.bestia.zoneserver.actor.zone;

import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.DoneMessage;
import net.bestia.messages.internal.StartInitMessage;
import net.bestia.model.domain.MapParameter;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.map.MapGeneratorMasterActor;
import net.bestia.zoneserver.map.MapService;

/**
 * Upon receiving the StartInit message the actor will start its work: Depending
 * on the given config it will generate a whole new world (by spawning the
 * needed worker actors chains) or it will restart the bestia service. This
 * means it will reload a given/saved map file and repopulate all the caches
 * with entity instances. If there is no data to be found it will default fall
 * back to a default world creation.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class InitGlobalActor extends BestiaActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private boolean hasInitialized = false;
	private int actorWaiting;
	
	private final MapService mapDataService;
	
	private final ActorRef mapGeneratorMaster;

	public InitGlobalActor(MapService mapDataService) {

		this.mapDataService = Objects.requireNonNull(mapDataService);
		
		this.mapGeneratorMaster = SpringExtension.actorOf(getContext(), MapGeneratorMasterActor.class);
	}
	
	@Override
	public void preStart() throws Exception {
		LOG.warning("INITGLOBAL STARTED");
		
		if(!mapDataService.isMapInitialized()) {
			initializeMap();
		}
	}
	
	private void initializeMap() {
		LOG.info("New map is generated.");
		MapParameter mapParams = MapParameter.fromAverageUserCount(1, "Terra");
		mapGeneratorMaster.tell(mapParams, getSelf());
	}
	

	@Override
	public void onReceive(Object message) throws Exception {

		if(message instanceof String) {
			if(((String)message).equals("okay")) {
				unhandled(message);
				return;
			}
			
			actorWaiting--;
			if(actorWaiting <= 0) {
				getContext().parent().tell(new DoneMessage("global"), getSelf());
			}
		}
		
		if (!(message instanceof StartInitMessage)) {
			
		}

		if (hasInitialized) {
			return;
		}
		
		hasInitialized = true;		

		// Start the initialization process.
		LOG.info("Start the global server initialization...");
		
		// This signalling does not work.
		getContext().parent().tell(new DoneMessage("global"), getSelf());
	}
}
