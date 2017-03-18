package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.zoneserver.actor.BestiaActor;

/**
 * The uplink actor is a basic entrypoint 
 * @author Thomas
 *
 */
@Component
@Scope("prototype")
public class UplinkActor extends BestiaActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	@Override
	public void onReceive(Object arg0) throws Throwable {
		
		LOG.info("MESSAGE RECEIVED ####");
	}

}
