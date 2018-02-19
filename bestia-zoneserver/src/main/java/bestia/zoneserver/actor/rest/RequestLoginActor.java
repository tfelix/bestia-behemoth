package bestia.zoneserver.actor.rest;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import bestia.messages.account.AccountLoginRequest;
import bestia.zoneserver.actor.zone.ClientMessageActor.RedirectMessage;
import bestia.zoneserver.service.LoginService;

/**
 * Performs a login operation and generates a new login token for the account.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class RequestLoginActor extends AbstractActor {

	public final static String NAME = "RESTrequestLogin";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final LoginService loginService;

	@Autowired
	public RequestLoginActor(LoginService loginService) {

		this.loginService = Objects.requireNonNull(loginService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(AccountLoginRequest.class, this::handleLogin)
				.build();
	}
	
	@Override
	public void preStart() throws Exception {
		RedirectMessage req = RedirectMessage.get(AccountLoginRequest.class);
		context().parent().tell(req, getSelf());
	}
	
	private void handleLogin(AccountLoginRequest msg) {
		LOG.debug("Received incoming login: {}", msg);

		final AccountLoginRequest newToken = loginService.setNewLoginToken(msg);

		getSender().tell(newToken, getSelf());
	}
}
