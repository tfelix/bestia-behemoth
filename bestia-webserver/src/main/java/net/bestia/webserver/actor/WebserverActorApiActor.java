package net.bestia.webserver.actor;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.TypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import net.bestia.messages.account.*;
import net.bestia.webserver.exceptions.WrongCredentialsException;
import net.bestia.webserver.messages.web.ClientPayloadMessage;
import net.bestia.webserver.messages.web.CloseConnection;
import net.bestia.webserver.messages.web.OpenConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.Objects;

/**
 * Typed actor to connect to the webserver in order to query the zoneservers.
 * 
 * @author Thomas Felix
 *
 */
public class WebserverActorApiActor implements WebserverActorApi {

	private final static Logger LOG = LoggerFactory.getLogger(WebserverActorApiActor.class);
	private final static Timeout REST_CALL_TIMEOUTS = new Timeout(Duration.create(5, "seconds"));

	private final ActorRef connections;
	private final ActorRef uplink;

	public WebserverActorApiActor(ActorRef uplink) {

		this.uplink = Objects.requireNonNull(uplink);
		
		final Props connectionProps = ConnectionsActor.props(uplink);
		final ActorContext context = TypedActor.context();
		this.connections = context.actorOf(connectionProps, "connections");
	}

	@Override
	public AccountLoginRequest getLoginToken(String accName, String password) {

		LOG.debug("REST loginTokenRequest: {}, pass: {}.", accName, password);

		final AccountLoginRequest data = new AccountLoginRequest(accName, password);

		try {
			final Future<Object> future = Patterns.ask(uplink, data, REST_CALL_TIMEOUTS);
			return (AccountLoginRequest) Await.result(future,
					REST_CALL_TIMEOUTS.duration());
		} catch (Exception e) {
			LOG.warn("Request for loginTokenRequest timed out: {}.", data);
			return null;
		}
	}

	@Override
	public void openWebsocketConnection(String sessionUid, WebSocketSession session) {

		LOG.debug("Opening new client socket: {}.", sessionUid);
		
		final OpenConnection prepCon = new OpenConnection(sessionUid, session);
		connections.tell(prepCon, ActorRef.noSender());
	}

	@Override
	public void closeWebsocketConnection(String sessionUid) {

		LOG.debug("closeWebsocketConnection: {}.", sessionUid);
		
		final CloseConnection closeMsg = new CloseConnection(sessionUid);
		connections.tell(closeMsg, ActorRef.noSender());
	}

	@Override
	public void handleClientMessage(String sessionUid, String payload) {
		
		final ClientPayloadMessage clientMsg = new ClientPayloadMessage(sessionUid, payload);
		connections.tell(clientMsg, ActorRef.noSender());

	}

	@Override
	public boolean setPassword(String accName, String oldPassword, String newPassword)
			throws WrongCredentialsException {

		LOG.debug("REST password reset: {}.", accName);

		final ChangePasswordRequest data = new ChangePasswordRequest(accName, oldPassword, newPassword);

		try {
			final Future<Object> future = Patterns.ask(uplink, data, REST_CALL_TIMEOUTS);
			return (Boolean) Await.result(future, REST_CALL_TIMEOUTS.duration());
		} catch (Exception e) {
			LOG.warn("Request for password change timed out: {}.", data);
			return false;
		}
	}

	@Override
	public UserNameCheck checkAvailableUserName(UserNameCheck data) {

		LOG.debug("REST user name check: {}.", data);

		try {
			final Future<Object> future = Patterns.ask(uplink, data, REST_CALL_TIMEOUTS);
			return (UserNameCheck) Await.result(future, REST_CALL_TIMEOUTS.duration());
		} catch (Exception e) {
			LOG.warn("Request for user name check timed out: {}.", data);
			return null;
		}

	}

	@Override
	public ServerStatusMessage requestServerStatus() {

		LOG.debug("REST server status requested.");

		try {
			final ServerStatusMessage.Request req = new ServerStatusMessage.Request();
			final Future<Object> future = Patterns.ask(uplink, req, REST_CALL_TIMEOUTS);
			return (ServerStatusMessage) Await.result(future,
					REST_CALL_TIMEOUTS.duration());
		} catch (Exception e) {
			LOG.warn("Request for server status timed out.");
			return null;
		}
	}

	@Override
	public AccountRegistrationReply registerAccount(AccountRegistration registration) {

		LOG.debug("REST account registration requested.");

		try {
			final Future<Object> future = Patterns.ask(uplink, registration, REST_CALL_TIMEOUTS);
			return (AccountRegistrationReply) Await.result(future,
					REST_CALL_TIMEOUTS.duration());
		} catch (Exception e) {
			LOG.warn("Request for registration timed out.");
			return null;
		}
	}
}
