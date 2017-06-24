package net.bestia.zoneserver.actor.login;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.web.AccountLogin;
import net.bestia.messages.web.AccountLoginToken;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.service.LoginService;

/**
 * Performs a login operation and generates a new login token for the account.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class RequestLoginActor extends BestiaActor {

	public final static String NAME = "requestLogin";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final LoginService loginService;

	@Autowired
	public RequestLoginActor(LoginService loginService) {

		this.loginService = Objects.requireNonNull(loginService);
	}

	private void handleLogin(AccountLogin msg) {
		LOG.debug("Received incoming login: {}", msg);

		final AccountLoginToken newToken = loginService.setNewLoginToken(msg.getUsername(), msg.getPassword());

		getSender().tell(newToken, getSelf());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(AccountLogin.class, this::handleLogin)
				.build();
	}

}
