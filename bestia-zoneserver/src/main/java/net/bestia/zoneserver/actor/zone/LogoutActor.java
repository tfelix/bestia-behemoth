package net.bestia.zoneserver.actor.zone;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.service.LoginService;

/**
 * Manages the connection state of a client. This is needed to perform certain
 * lookups to retrieve the current actor path of the webserver a client is
 * connected to.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class LogoutActor extends BestiaRoutingActor {

	public static final String NAME = "logout";

	private final LoginService loginService;

	@Autowired
	public LogoutActor(LoginService loginService) {
		super(Arrays.asList(ClientConnectionStatusMessage.class));

		this.loginService = Objects.requireNonNull(loginService);
	}

	@Override
	protected void handleMessage(Object msg) {

		final ClientConnectionStatusMessage ccmsg = (ClientConnectionStatusMessage) msg;

		if (!(ccmsg.getState() == ConnectionState.CONNECTED)) {
			// Unregister.
			loginService.login(ccmsg.getAccountId());
		}
	}
}
