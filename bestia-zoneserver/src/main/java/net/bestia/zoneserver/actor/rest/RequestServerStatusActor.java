package net.bestia.zoneserver.actor.rest;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import net.bestia.messages.account.ServerStatusMessage;
import net.bestia.model.server.MaintenanceLevel;
import net.bestia.zoneserver.actor.zone.ClientMessageHandlerActor.RedirectMessage;
import net.bestia.zoneserver.configuration.RuntimeConfigService;

/**
 * Sends the server login status to the user.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class RequestServerStatusActor extends AbstractActor {

	public final static String NAME = "RESTserverStatus";

	private final RuntimeConfigService config;

	@Autowired
	public RequestServerStatusActor(RuntimeConfigService config) {

		this.config = Objects.requireNonNull(config);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ServerStatusMessage.Request.class, x -> handleStatusRequest())
				.build();
	}
	
	@Override
	public void preStart() throws Exception {
		final RedirectMessage req = RedirectMessage.get(ServerStatusMessage.Request.class);
		context().parent().tell(req, getSelf());
	}

	private void handleStatusRequest() {

		final MaintenanceLevel level = config.getMaintenanceMode();

		final ServerStatusMessage reply = new ServerStatusMessage(level, "");

		// Reply the message.
		getSender().tell(reply, getSelf());
	}
}
