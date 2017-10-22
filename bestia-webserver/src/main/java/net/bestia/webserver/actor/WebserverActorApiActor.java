package net.bestia.webserver.actor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.TypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import net.bestia.messages.account.AccountLoginRequest;
import net.bestia.messages.account.AccountRegistration;
import net.bestia.messages.account.AccountRegistrationReply;
import net.bestia.messages.account.ChangePasswordRequest;
import net.bestia.messages.account.ServerStatusMessage;
import net.bestia.messages.account.UserNameCheck;
import net.bestia.webserver.exceptions.WrongCredentialsException;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

/**
 * Typed actor to connect to the webserver in order to query the zoneservers.
 * 
 * @author Thomas Felix
 *
 */
public class WebserverActorApiActor implements WebserverActorApi {

	private final static Logger LOG = LoggerFactory.getLogger(WebserverActorApiActor.class);
	private final static Timeout REST_CALL_TIMEOUTS = new Timeout(Duration.create(5, "seconds"));

	private final ActorContext context = TypedActor.context();
	private final ObjectMapper mapper = new ObjectMapper();
	private final Map<String, ActorRef> openedSockets = new HashMap<>();

	private final ActorRef uplink;

	public WebserverActorApiActor(ActorRef uplink) {

		this.uplink = Objects.requireNonNull(uplink);
	}

	@Override
	public AccountLoginRequest getLoginToken(String accName, String password) {

		LOG.debug("REST loginTokenRequest: {}, pass: {}.", accName, password);

		final AccountLoginRequest data = new AccountLoginRequest(accName, password);

		try {
			final Future<Object> future = Patterns.ask(uplink, data, REST_CALL_TIMEOUTS);
			final AccountLoginRequest result = (AccountLoginRequest) Await.result(future,
					REST_CALL_TIMEOUTS.duration());
			return result;
		} catch (Exception e) {
			LOG.warn("Request for loginTokenRequest timed out: {}.", data);
			return null;
		}
	}

	@Override
	public void setupWebsocketConnection(String sessionUid, WebSocketSession session) {

		LOG.debug("Starting new client socket: {}.", sessionUid);

		// Setup the actor to access the zone server cluster.
		final String actorName = String.format("socket-%s", sessionUid);
		final Props messageHandlerProps = ClientSocketActor.props(session, mapper, uplink).withDeploy(Deploy.local());
		final ActorRef messageActor = context.actorOf(messageHandlerProps, actorName);
		openedSockets.put(sessionUid, messageActor);
	}

	@Override
	public void closeWebsocketConnection(String sessionUid) {

		LOG.debug("closeWebsocketConnection: {}.", sessionUid);

		if (!openedSockets.containsKey(sessionUid)) {
			LOG.warn("No opened connection with uid: {}", sessionUid);
			return;
		}

		openedSockets.get(sessionUid).tell(PoisonPill.getInstance(), ActorRef.noSender());
		openedSockets.remove(sessionUid);
	}

	@Override
	public void handleClientMessage(String sessionUid, String payload) {
		if (!openedSockets.containsKey(sessionUid)) {
			LOG.warn("No opened connection with uid: {}", sessionUid);
			return;
		}

		openedSockets.get(sessionUid).tell(payload, ActorRef.noSender());
	}

	@Override
	public boolean setPassword(String accName, String oldPassword, String newPassword)
			throws WrongCredentialsException {

		LOG.debug("REST password reset: {}.", accName);

		final ChangePasswordRequest data = new ChangePasswordRequest(accName, oldPassword, newPassword);

		try {
			final Future<Object> future = Patterns.ask(uplink, data, REST_CALL_TIMEOUTS);
			final Boolean result = (Boolean) Await.result(future, REST_CALL_TIMEOUTS.duration());
			return result;
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
			final UserNameCheck result = (UserNameCheck) Await.result(future, REST_CALL_TIMEOUTS.duration());
			return result;
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
			final ServerStatusMessage result = (ServerStatusMessage) Await.result(future,
					REST_CALL_TIMEOUTS.duration());
			return result;
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
			final AccountRegistrationReply result = (AccountRegistrationReply) Await.result(future,
					REST_CALL_TIMEOUTS.duration());
			return result;
		} catch (Exception e) {
			LOG.warn("Request for registration timed out.");
			return null;
		}
	}
}
