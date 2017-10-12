package net.bestia.zoneserver.actor.connection;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import net.bestia.messages.cluster.ClientConnectionStatusMessage;
import net.bestia.messages.cluster.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;
import net.bestia.zoneserver.service.LoginService;

/**
 * Manages the connection state of a client. If this actor detects the
 * disconnection of a client this will perform certain cleanup tasks on the
 * server.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ConnectionStatusActor extends AbstractActor {

	public static final String NAME = "logout";

	private final LoginService loginService;

	@Autowired
	public ConnectionStatusActor(LoginService loginService) {

		this.loginService = Objects.requireNonNull(loginService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ClientConnectionStatusMessage.class, this::onConnectionStateChanged)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		final RedirectMessage msg = RedirectMessage.get(ClientConnectionStatusMessage.class);
		context().parent().tell(msg, getSelf());
	}

	private void onConnectionStateChanged(ClientConnectionStatusMessage msg) {
		final ClientConnectionStatusMessage ccmsg = (ClientConnectionStatusMessage) msg;

		if (ccmsg.getState() == ConnectionState.CONNECTED) {
			// TODO Spawn all player entities. Do this logic here not in the
			// login service anymore.
		} else {
			// Unregister.
			loginService.logout(ccmsg.getAccountId());
		}
	}

}
