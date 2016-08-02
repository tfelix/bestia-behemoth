package net.bestia.zoneserver.actor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.Message;
import net.bestia.messages.inventory.InventoryListRequestMessage;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.actor.inventory.InventoryActor;
import net.bestia.zoneserver.actor.login.LoginActor;
import net.bestia.zoneserver.actor.system.RespondActor;

/**
 * Central actor for handling zone related messages. This actor will redirect
 * the various massages to the different actors.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ZoneActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final ActorRef clientResponseActor;
	private final ActorRef loginActor;
	private final ActorRef inventoryListActor;

	public ZoneActor(BestiaActorContext ctx) {
		
		clientResponseActor = getContext().actorOf(RespondActor.props(ctx), "response");
		loginActor = getContext().actorOf(LoginActor.props(ctx), "login");
		inventoryListActor = getContext().actorOf(InventoryActor.props(ctx), "inventory");
	}

	public static Props props(BestiaActorContext ctx) {
		return Props.create(ZoneActor.class, ctx);
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (!(message instanceof Message)) {
			unhandled(message);
			LOG.warning("Zone received unknown message: {}", message);
			return;
		}

		final Message msg = (Message) message;

		switch (msg.getMessageId()) {
		case InventoryListRequestMessage.MESSAGE_ID:

			break;
		}

	}
}
