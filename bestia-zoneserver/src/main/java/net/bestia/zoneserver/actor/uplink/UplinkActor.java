package net.bestia.zoneserver.actor.uplink;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorSelection;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.actor.zone.MessageRoutingActor;
import net.bestia.zoneserver.actor.zone.ZoneActor;

/**
 * The uplink actor is a basic entrypoint to the bestia zone server from
 * external.
 * 
 * @author Thomas Felix
 *
 */
@Component("UplinkActor")
@Scope("prototype")
public class UplinkActor extends BestiaActor {

	public final static String NAME = "uplink";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final ActorSelection zone;
	private final ActorSelection zoneRouter;

	public UplinkActor() {

		zone = getContext().actorSelection(AkkaCluster.getNodeName(ZoneActor.NAME));
		zoneRouter = getContext().actorSelection(AkkaCluster.getNodeName(MessageRoutingActor.NAME));
	}

	@Override
	public void onReceive(Object msg) throws Throwable {

		LOG.debug("Received incoming message: {}", msg);

		// Currently testing the chat message with the new routing technology.
		if (msg instanceof ChatMessage) {
			zoneRouter.tell(msg, getSender());
		} else {
			zone.tell(msg, getSender());
		}

	}

}
