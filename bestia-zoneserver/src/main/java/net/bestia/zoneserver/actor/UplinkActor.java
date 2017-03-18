package net.bestia.zoneserver.actor;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;

@Component
@Scope("prototype")
public class UplinkActor extends BestiaActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	@Override
	public void onReceive(Object arg0) throws Throwable {
		
		LOG.info("MESSAGE RECEIVED ####");
	}

}
