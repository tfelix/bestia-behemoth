package net.bestia.zoneserver.actor.login;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.web.AccountLogin;
import net.bestia.messages.web.AccountLoginToken;
import net.bestia.zoneserver.actor.BestiaActor;

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

	public RequestLoginActor() {

		
	}

	@Override
	public void onReceive(Object msg) throws Throwable {

		if(!(msg instanceof AccountLogin)) {
			unhandled(msg);
			return;
		}
		
		final AccountLogin token = (AccountLogin) msg;
		
		LOG.debug("Received incoming login: {}", token);
		
		// Check login.
		final AccountLoginToken answerToken = new AccountLoginToken(1, "rocket", "test1234");
		getSender().tell(answerToken, getSelf());
	}

}
