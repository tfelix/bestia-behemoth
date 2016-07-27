package net.bestia.zoneserver.actor.server;

import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.messages.AccountMessage;
import net.bestia.messages.ClientResponseMessage;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.service.ClientRefLookup;

/**
 * The {@link RespondActor} will lookup the information (actor ref) of the
 * current account, serialize the message and send it back to where it belongs.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class RespondActor extends UntypedActor {

	final private LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final ClientRefLookup lookup;
	private final ObjectMapper jsonMapper;

	public RespondActor(BestiaActorContext ctx) {

		Objects.requireNonNull(ctx);

		this.lookup = ctx.getSpringContext().getBean(ClientRefLookup.class);
		this.jsonMapper = ctx.getJsonMapper();
	}

	public static Props props(final BestiaActorContext ctx) {
		// Props must be deployed locally since we contain a non serializable
		return Props.create(new Creator<RespondActor>() {
			private static final long serialVersionUID = 1L;

			public RespondActor create() throws Exception {
				return new RespondActor(ctx);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (message instanceof AccountMessage) {

			final AccountMessage accMsg = (AccountMessage) message;
			final ActorRef origin = lookup.getActorRef(accMsg.getAccountId());

			if (origin == null) {
				LOG.warning("Could not find origin ref for message: {}", message.toString());
				unhandled(message);
				return;
			}

			// Prepare the client message.
			final String payload = jsonMapper.writeValueAsString(message);
			final ClientResponseMessage responseMsg = new ClientResponseMessage(accMsg.getAccountId(), payload);
			origin.tell(responseMsg, getSelf());
		} else {

			unhandled(message);
		}

	}

}
