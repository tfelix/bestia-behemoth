package net.bestia.zoneserver.actor.login;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.Message;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.service.ServerRuntimeConfiguration;

/**
 * This actor will take {@link LoginRequestMessage} and check the validity of
 * the token. If the message has a valid one then the login is granted. There
 * might be also other conditions like a restricted login when the server is in
 * maintenance mode.
 * <p>
 * A {@link DistributedPubSubMediator} is used in order to provide a random
 * cluster wide routing logic for incoming messages.
 * </p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component("LoginActor")
@Scope("prototype")
public class LoginActor extends BestiaRoutingActor {

	public static final String NAME = "login";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final Set<Class<? extends Message>> HANDLED_CLASSES = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(LoginAuthMessage.class)));

	private final AccountDAO accountDao;
	private final ServerRuntimeConfiguration config;

	@Autowired
	public LoginActor(AccountDAO accountDao, ServerRuntimeConfiguration config) {
		
		this.accountDao = accountDao;
		this.config = config;
	}

	private void respond(LoginAuthMessage msg, LoginState state, Account acc) {
		final LoginAuthReplyMessage response = new LoginAuthReplyMessage(state);
		
		if(acc != null) {
			response.setAccountId(acc.getId());
		}
		
		getSender().tell(response, getSelf());
	}
	
	@Override
	protected Set<Class<? extends Message>> getHandledMessages() {
		return HANDLED_CLASSES;
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("LoginRequestMessage received: {}", msg.toString());

		final LoginAuthMessage loginMsg = (LoginAuthMessage) msg;
		
		if(config.isMaintenanceMode()) {
			// We only allow server admins to be online during a maintenance.
			respond(loginMsg, LoginState.DENIED, null);
			return;
		}

		// Check to see if the find the requested account.
		final Account acc = accountDao.findOne(loginMsg.getAccountId());

		if (acc == null) {
			respond(loginMsg, LoginState.DENIED, null);
			return;
		}

		if (acc.getLoginToken().equals(loginMsg.getToken())) {
			respond(loginMsg, LoginState.ACCEPTED, acc);
		} else {
			respond(loginMsg, LoginState.DENIED, null);
		}
	}

}
