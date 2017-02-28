package net.bestia.zoneserver.actor.zone;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.login.EngineReadyMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;


@Component
@Scope("prototype")
public class EngineReadyActor extends BestiaRoutingActor {

	public static final String NAME = "engineReadyActor";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	/**
	 * 
	 */
	@Autowired
	public EngineReadyActor() {
		super(Arrays.asList(EngineReadyMessage.class));

	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("LoginRequestMessage received: {}", msg.toString());

		final EngineReadyMessage readyMsg = (EngineReadyMessage) msg;


	}

}
