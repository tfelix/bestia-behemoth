package net.bestia.zoneserver.actor.server;

import java.util.Objects;

import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import net.bestia.messages.system.ClientConnectionStatusMessage;
import net.bestia.messages.system.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.server.BestiaActorContext;
import net.bestia.zoneserver.service.ClientRefLookup;

/**
 * Manages new and dropping connections of clients in the cluster.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ConnectionManagerActor extends UntypedActor {
	
	private final ClientRefLookup lookup;

	public ConnectionManagerActor(BestiaActorContext ctx) {

		Objects.requireNonNull(ctx);

		this.lookup = ctx.getSpringContext().getBean(ClientRefLookup.class);
	}

	public static Props props(final BestiaActorContext ctx) {
		// Props must be deployed locally since we contain a non serializable
		return Props.create(new Creator<ConnectionManagerActor>() {
			private static final long serialVersionUID = 1L;

			public ConnectionManagerActor create() throws Exception {
				return new ConnectionManagerActor(ctx);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public void onReceive(Object message) throws Exception {
		
		if(message instanceof ClientConnectionStatusMessage) {
			final ClientConnectionStatusMessage msg = (ClientConnectionStatusMessage) message;
			
			if(msg.getState() == ConnectionState.CONNECTED) {
				// Add ref.
				lookup.setActorRef(msg.getAccountId(), msg.getWebserverRef());
			} else {
				// Remove ref.
				lookup.removeActorRef(msg.getAccountId());
			}
		}

	}

}
