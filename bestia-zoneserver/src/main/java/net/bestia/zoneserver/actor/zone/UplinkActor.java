package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.actor.SpringExtension;

/**
 * The uplink actor is a basic entrypoint 
 * @author Thomas
 *
 */
@Component
@Scope("prototype")
public class UplinkActor extends BestiaActor {
	
	public final static String NAME = "uplink";
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private final ActorRef zone;
	
	public UplinkActor() {
		
		zone = SpringExtension.actorOf(getContext().system(), ZoneActor.class);
	}

	@Override
	public void onReceive(Object msg) throws Throwable {
		
		LOG.debug("Received incoming message: {}", msg);
		zone.tell(msg, getSender());
	}

}
