package net.bestia.zoneserver.actor.entity;

import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.UntypedActor;
import net.bestia.messages.bestia.BestiaActivatedMessage;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.service.ActiveBestiaManager;

/**
 * Upon receiving an activation request from this account we check if the
 * account is able to uses this bestia. It will then get activated and all
 * necessairy information is send to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ActivateBestiaActor extends UntypedActor {

	private final ActiveBestiaManager activeBestias;

	public ActivateBestiaActor(BestiaActorContext ctx) {

		this.activeBestias = ctx.getSpringContext().getBean(ActiveBestiaManager.class);
	}

	public static Props props(final BestiaActorContext ctx) {
		// Props must be deployed locally since we contain a dao (non
		// serializable)
		return Props.create(ActivateBestiaActor.class, ctx).withDeploy(Deploy.local());
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (message instanceof BestiaActivatedMessage) {
			final BestiaActivatedMessage msg = (BestiaActivatedMessage) message;
			
			// TODO Aus alter Logik übertragen und Entity aktivieren.

		} else {
			unhandled(message);
		}
	}

}
