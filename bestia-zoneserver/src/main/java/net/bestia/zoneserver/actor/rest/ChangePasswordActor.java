package net.bestia.zoneserver.actor.rest;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.web.ChangePasswordRequest;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.zone.IngestExActor;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;
import net.bestia.zoneserver.service.AccountService;

/**
 * Changes the password of a user.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ChangePasswordActor extends AbstractActor {

	public final static String NAME = "RESTchangePassword";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final AccountService accService;

	@Autowired
	public ChangePasswordActor(AccountService accService) {

		this.accService = Objects.requireNonNull(accService);
	}

	@Override
	public Receive createReceive() {

		final RedirectMessage req = RedirectMessage.get(ChangePasswordRequest.class);
		getContext().actorSelection(AkkaCluster.getNodeName(IngestExActor.NAME)).tell(req, getSelf());

		return receiveBuilder()
				.match(ChangePasswordRequest.class, this::handleChangePassword)
				.build();
	}

	private void handleChangePassword(ChangePasswordRequest data) {

		LOG.debug("Check data: {}", data);

		final boolean wasSuccess = accService.changePassword(data.getAccountName(), 
				data.getOldPassword(),
				data.getNewPassword());

		// Reply the message.
		getSender().tell(wasSuccess, getSelf());
	}
}
