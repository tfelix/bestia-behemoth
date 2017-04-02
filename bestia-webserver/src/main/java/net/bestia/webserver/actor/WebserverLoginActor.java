package net.bestia.webserver.actor;

import java.util.Objects;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope;
import akka.util.Timeout;
import net.bestia.messages.web.AccountLogin;
import net.bestia.messages.web.AccountLoginToken;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class WebserverLoginActor implements WebserverLogin {

	private final static Logger LOG = LoggerFactory.getLogger(WebserverLoginActor.class);
	private final static Timeout timeout = new Timeout(Duration.create(2, "seconds"));

	private final ActorRef uplinkRouter;

	public WebserverLoginActor(ActorRef uplinkRouter) {
	
		this.uplinkRouter = Objects.requireNonNull(uplinkRouter);
	}

	@Override
	public AccountLoginToken getLoginToken(String accName, String password) {
		
		LOG.trace("Sending login: {}, pass: {} to the cluster.", accName, password);
		
		final AccountLogin accountLogin = new AccountLogin(accName, password);
		
		Future<Object> answer = Patterns.ask(uplinkRouter, new ConsistentHashableEnvelope(accountLogin, accountLogin), timeout);
		try {
			return (AccountLoginToken) Await.ready(answer, timeout.duration());
		} catch (TimeoutException | InterruptedException e) {
			LOG.warn("Login was not checked in time.");
			return null;
		}
	}

}
