package net.bestia.zoneserver.actor.login;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorPath;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.configuration.CacheConfiguration;
import net.bestia.zoneserver.service.CacheManager;
import net.bestia.zoneserver.service.PlayerEntityService;

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
public class DisconnectManagerActor extends BestiaRoutingActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "disconnectManager";

	private final CacheManager<Long, ActorPath> clientCache;
	private final PlayerEntityService entityService;

	@Autowired
	public DisconnectManagerActor(
			@Qualifier(CacheConfiguration.CLIENT_CACHE) CacheManager<Long, ActorPath> clientCache,
			PlayerEntityService entityService) {
		super(Arrays.asList(ClientConnectionStatusMessage.class));

		this.clientCache = Objects.requireNonNull(clientCache);
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	protected void handleMessage(Object msg) {

		final ClientConnectionStatusMessage ccmsg = (ClientConnectionStatusMessage) msg;

		if (!(ccmsg.getState() == ConnectionState.CONNECTED)) {
			// Unregister.
			LOG.debug("Client disconnected: {}.", ccmsg);
			clientCache.remove(ccmsg.getAccountId());

			// Remove all bestias entities for this account.
			LOG.debug(String.format("DeSpawning bestias for acc id: %d", ccmsg.getAccountId()));
			entityService.removePlayerBestias(ccmsg.getAccountId());
			
		}
	}
}
