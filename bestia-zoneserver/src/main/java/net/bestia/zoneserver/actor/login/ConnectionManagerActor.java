package net.bestia.zoneserver.actor.login;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.system.ClientConnectionStatusMessage;
import net.bestia.messages.system.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.configuration.CachesConfiguration;
import net.bestia.zoneserver.service.CacheManager;

/**
 * Manages the connection state of a client. This is needed to perform certain
 * lookups to retrieve the current actor path of the webserver a client is
 * connected to.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ConnectionManagerActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final CacheManager<Long, ActorRef> clientCache;

	@SuppressWarnings("unchecked")
	public ConnectionManagerActor(BestiaActorContext ctx) {

		this.clientCache = ctx.getSpringContext().getBean(CachesConfiguration.CLIENT_CACHE, CacheManager.class);
	}

	public static Props props(final BestiaActorContext ctx) {
		return Props.create(ConnectionManagerActor.class, ctx);
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
