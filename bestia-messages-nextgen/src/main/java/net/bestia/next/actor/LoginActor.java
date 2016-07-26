package net.bestia.next.actor;

import java.util.Objects;

import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.next.messages.LoginRequestMessage;
import net.bestia.next.messages.LoginResponseMessage;
import net.bestia.next.messages.LoginState;

/**
 * This actor will take {@link LoginRequestMessage} and check the validity of
 * the token. If the message has a valid one then the login is granted. There
 * might be also other conditions like a restricted login when the server is in
 * maintenance mode.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LoginActor extends UntypedActor {
	
	final private LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private final AccountDAO accountDao;
	
	public LoginActor(AccountDAO accountDao) {
		this.accountDao = Objects.requireNonNull(accountDao);
	}

	public static Props props(final AccountDAO accountDao) {
		return Props.create(new Creator<LoginActor>() {
			private static final long serialVersionUID = 1L;

			public LoginActor create() throws Exception {
				return new LoginActor(accountDao);
			}
		});
	}

	@Override
	public void onReceive(Object message) throws Exception {
		
		if(!(message instanceof LoginRequestMessage)) {
			unhandled(message);
			return;
		}
		
		LOG.debug("LoginRequestMessage received: {}", message.toString());
		
		// TODO Check if we are in maintenance mode.
		
		final LoginRequestMessage msg = (LoginRequestMessage) message;
		
		// Check to see if the find the requested account.
		final Account acc = accountDao.findOne(msg.getAccountId());
		
		if(acc == null) {
			respond(msg, LoginState.DENIED);
			return;
		}
		
		if(acc.getLoginToken().equals(msg.getToken())) {
			respond(msg, LoginState.ACCEPTED);
		} else {
			respond(msg, LoginState.DENIED);
		}
	}
	
	private void respond(LoginRequestMessage msg, LoginState state) {
		final LoginResponseMessage response = new LoginResponseMessage(msg, state);
		getSender().tell(response, getSelf());
		self().tell(PoisonPill.getInstance(), getSelf());
	}

}
