package net.bestia.zoneserver.actor.login;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.system.ClientConnectionStatusMessage;
import net.bestia.messages.system.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.zoneserver.configuration.CacheConfiguration;
import net.bestia.zoneserver.service.CacheManager;

/**
 * Manages the connection state of a client. This is needed to perform certain
 * lookups to retrieve the current actor path of the webserver a client is
 * connected to.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class ConnectionManagerActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final CacheManager<Long, ActorRef> clientCache;

	public ConnectionManagerActor(
			@Qualifier(CacheConfiguration.CLIENT_CACHE) CacheManager<Long, ActorRef> clientCache) {

		this.clientCache = clientCache;
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (!(message instanceof ClientConnectionStatusMessage)) {
			unhandled(message);
			return;
		}

		final ClientConnectionStatusMessage ccmsg = (ClientConnectionStatusMessage) message;

		if (ccmsg.getState() == ConnectionState.CONNECTED) {
			// Register.
			LOG.debug("Client registered: {}.", ccmsg);
			clientCache.set(ccmsg.getAccountId(), ccmsg.getWebserverRef());
		} else {
			// Unregister.
			LOG.debug("Client removed: {}.", ccmsg);
			clientCache.remove(ccmsg.getAccountId());
		}
	}
}
