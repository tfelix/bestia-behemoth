package net.bestia.zoneserver.actor.login;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.Message;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
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
public class ConnectionManagerActor extends BestiaRoutingActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "connectionManager";
	
	private final Set<Class<? extends Message>> HANDLED_CLASSES = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(ClientConnectionStatusMessage.class)));
	private final CacheManager<Long, ActorRef> clientCache;

	@Autowired
	public ConnectionManagerActor(
			@Qualifier(CacheConfiguration.CLIENT_CACHE) CacheManager<Long, ActorRef> clientCache) {

		this.clientCache = clientCache;
	}

	@Override
	protected Set<Class<? extends Message>> getHandledMessages() {
		return HANDLED_CLASSES;
	}

	@Override
	protected void handleMessage(Object msg) {

		final ClientConnectionStatusMessage ccmsg = (ClientConnectionStatusMessage) msg;

		if (ccmsg.getState() == ConnectionState.CONNECTED) {
			// Register.
			LOG.debug("Client connected: {}.", ccmsg);
			clientCache.set(ccmsg.getAccountId(), ccmsg.getWebserverRef());
		} else {
			// Unregister.
			LOG.debug("Client disconnected: {}.", ccmsg);
			clientCache.remove(ccmsg.getAccountId());
		}
	}
}
