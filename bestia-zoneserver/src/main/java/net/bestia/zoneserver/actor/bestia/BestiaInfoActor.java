package net.bestia.zoneserver.actor.bestia;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.Message;
import net.bestia.messages.bestia.RequestBestiaInfoMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

public class BestiaInfoActor extends BestiaRoutingActor {
	
	public static final String NAME = "bestiaInfo";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final Set<Class<? extends Message>> HANDLED_CLASSES = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(RequestBestiaInfoMessage.class)));

	@Override
	protected Set<Class<? extends Message>> getHandledMessages() {
		return HANDLED_CLASSES;
	}
	
	@Override
	protected void handleMessage(Object msg) {
		
		
		
	}

}
