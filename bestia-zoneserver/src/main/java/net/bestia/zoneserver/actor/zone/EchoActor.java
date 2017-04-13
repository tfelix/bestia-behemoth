package net.bestia.zoneserver.actor.zone;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.zoneserver.actor.BestiaActor;

/**
 * Simple test for routing setup.
 * 
 * @author Thomas
 *
 */
@Component
@Scope("prototype")
public class EchoActor extends BestiaActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	@Override
	public void onReceive(Object msg) throws Throwable {
		
		if(msg instanceof String) {
			LOG.info("Received Message {} in Actor {}", (String) msg, self().path().name());
		}
		
	}

}
