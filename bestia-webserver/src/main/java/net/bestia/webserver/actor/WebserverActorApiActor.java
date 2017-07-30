package net.bestia.webserver.actor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.TypedActor;
import akka.pattern.Patterns;
import akka.routing.ConsistentHashingRouter;
import akka.util.Timeout;
import net.bestia.messages.web.AccountLogin;
import net.bestia.messages.web.AccountLoginToken;
import net.bestia.webserver.exceptions.WrongCredentialsException;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class WebserverActorApiActor implements WebserverActorApi {

	private final static Logger LOG = LoggerFactory.getLogger(WebserverActorApiActor.class);
	private final static Timeout timeout = new Timeout(Duration.create(2, "seconds"));

	private final ActorRef uplinkRouter;
	private final ActorContext context;
	private final ObjectMapper mapper;

	private final Map<String, ActorRef> openedSockets = new HashMap<>();

	public WebserverActorApiActor(ActorRef uplinkRouter) {

		this.uplinkRouter = Objects.requireNonNull(uplinkRouter);
		this.context = TypedActor.context();
		this.mapper = new ObjectMapper();
	}

	@Override
	public AccountLoginToken getLoginToken(String accName, String password) {

		LOG.trace("Sending login: {}, pass: {} to the cluster.", accName, password);

		final AccountLogin accountLogin = new AccountLogin(accName, password);

		Future<Object> answer = Patterns.ask(uplinkRouter,
				new ConsistentHashingRouter.ConsistentHashableEnvelope(accountLogin, accountLogin),
				timeout);

		try {
			return (AccountLoginToken) Await.ready(answer, timeout.duration());
		} catch (TimeoutException | InterruptedException e) {
			LOG.warn("Login was not checked in time.");
			return null;
		}
	}

	@Override
	public void setupWebsocketConnection(String sessionUid, WebSocketSession session) {
		// Setup the actor to access the zone server cluster.
		final String actorName = String.format("socket-%s", session.getId());
		final Props messageHandlerProps = ClientMessageHandlerActor.props(session, mapper, uplinkRouter);
		final ActorRef messageActor = context.actorOf(messageHandlerProps, actorName);
		openedSockets.put(sessionUid, messageActor);
	}

	@Override
	public void closeWebsocketConnection(String sessionUid) {

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

		// FIXME Einbauen.
		throw new IllegalStateException("Not implemented yet.");
	}

}
