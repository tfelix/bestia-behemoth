package net.bestia.zoneserver.actor.zone;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.MessageId;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.messages.login.EngineReadyMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.actor.chat.ChatActor;

/**
 * This actor will once be the central routing actor which will resend all the
 * incoming messages to the correct destinations on the bestia system as soon as
 * the monolithic actor hierarchy is broken up into smaller parts.
 * 
 * It will also simplify the routing logic and helps to make the system easier
 * scalable via configuration files.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class MessageRoutingActor extends BestiaActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private static final Map<String, String> MESSAGE_TO_ROUTE = new HashMap<>();

	public static final String NAME = "messageRouter";

	static {
		/**
		 * This temporarily sets the routes of the incoming messages to the actor
		 * which will then receive them.
		 */
		MESSAGE_TO_ROUTE.put(ChatMessage.MESSAGE_ID, AkkaCluster.getNodeName(ChatActor.NAME));
		MESSAGE_TO_ROUTE.put(EngineReadyMessage.MESSAGE_ID, AkkaCluster.getNodeName(EngineReadyActor.NAME));
	}

	@Override
	public void onReceive(Object msg) throws Throwable {

		// TODO Maybe we need some more handling here for other messages.
		if (msg instanceof MessageId) {

			String msgId = ((MessageId) msg).getMessageId();

			if (!MESSAGE_TO_ROUTE.containsKey(msgId)) {
				LOG.warning("Message ID {} encountered but routing list has no entry for receiver.", msgId);
				unhandled(msg);
			}

			getContext().actorSelection(MESSAGE_TO_ROUTE.get(msgId)).tell(msg, getSender());

		} else {
			unhandled(msg);
		}

	}

}
