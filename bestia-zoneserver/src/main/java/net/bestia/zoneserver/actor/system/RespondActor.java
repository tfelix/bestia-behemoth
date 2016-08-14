package net.bestia.zoneserver.actor.system;

import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.AccountMessage;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.actor.login.ConnectionManagerActor;
import net.bestia.zoneserver.configuration.CachesConfiguration;
import net.bestia.zoneserver.service.CacheManager;

/**
 * The {@link RespondActor} will lookup the information (actor ref) of the
 * current account, serialize the message and send it back to where it belongs.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class RespondActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private final CacheManager<Long, ActorRef> clientCache;

	@SuppressWarnings("unchecked")
	public RespondActor(BestiaActorContext ctx) {

		this.clientCache = ctx.getSpringContext().getBean(CachesConfiguration.CLIENT_CACHE, CacheManager.class);
	}

	public static Props props(final BestiaActorContext ctx) {
		return Props.create(ConnectionManagerActor.class, ctx).withDeploy(Deploy.local());
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

			unhandled(message);
		}

	}

}
