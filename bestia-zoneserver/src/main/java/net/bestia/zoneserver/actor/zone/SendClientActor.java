package net.bestia.zoneserver.actor.zone;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.AccountMessage;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.configuration.CacheConfiguration;
import net.bestia.zoneserver.service.CacheManager;

/**
 * The {@link SendClientActor} is responsible for the delivery of messages
 * directed to clients. It will lookup the information (actor ref) of the the
 * account given in the account message, serialize the message and send it back
 * to the webserver the client is currently connected.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class SendClientActor extends BestiaActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final CacheManager<Long, ActorRef> clientCache;

	public SendClientActor(
			@Qualifier(CacheConfiguration.CLIENT_CACHE) CacheManager<Long, ActorRef> clientCache) {

		this.clientCache = clientCache;
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (message instanceof AccountMessage) {

			final AccountMessage accMsg = (AccountMessage) message;
			final ActorRef origin = clientCache.get(accMsg.getAccountId());

			if (origin == null) {
				LOG.warning("Could not find origin ref for message: {}", message.toString());
				unhandled(message);
				return;
			}

			// Send the client message.
			origin.tell(message, getSelf());
		} else {
			// We handle only account messages.
			unhandled(message);
		}

	}

}
