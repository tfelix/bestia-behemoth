package bestia.zoneserver.actor.rest;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import bestia.messages.account.ChangePasswordRequest;
import bestia.zoneserver.actor.zone.ClientMessageActor.RedirectMessage;
import bestia.zoneserver.service.AccountService;

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
		return receiveBuilder()
				.match(ChangePasswordRequest.class, this::handleChangePassword)
				.build();
	}
	
	@Override
	public void preStart() throws Exception {
		final RedirectMessage req = RedirectMessage.get(ChangePasswordRequest.class);
		context().parent().tell(req, getSelf());
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
