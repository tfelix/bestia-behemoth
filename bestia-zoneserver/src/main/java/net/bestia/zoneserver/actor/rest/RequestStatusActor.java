package net.bestia.zoneserver.actor.rest;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import net.bestia.messages.web.ChangePasswordRequest;
import net.bestia.messages.web.ServerStatusMessage;
import net.bestia.model.server.MaintenanceLevel;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.zone.IngestExActor;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectRequestMessage;
import net.bestia.zoneserver.configuration.RuntimeConfigService;

/**
 * Sends the server login status to the user.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class RequestStatusActor extends AbstractActor {

	public final static String NAME = "RESTserverStatus";

	private final RuntimeConfigService config;

	@Autowired
	public RequestStatusActor(RuntimeConfigService config) {

		this.config = Objects.requireNonNull(config);
	}

	@Override
	public Receive createReceive() {

		final RedirectRequestMessage req = RedirectRequestMessage.get(ChangePasswordRequest.class);
		getContext().actorSelection(AkkaCluster.getNodeName(IngestExActor.NAME)).tell(req, getSelf());

		return receiveBuilder()
				.match(ServerStatusMessage.Request.class, x -> handleStatusRequest())
				.build();
	}

	private void handleStatusRequest() {

		final MaintenanceLevel level = config.getMaintenanceMode();

		final ServerStatusMessage reply = new ServerStatusMessage(level, "");

		// Reply the message.
		getSender().tell(reply, getSelf());
	}
}
