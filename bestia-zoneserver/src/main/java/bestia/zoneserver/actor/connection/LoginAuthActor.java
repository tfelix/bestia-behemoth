package bestia.zoneserver.actor.connection;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import bestia.messages.login.LoginAuthMessage;
import bestia.messages.login.LoginAuthReplyMessage;
import bestia.messages.login.LoginState;
import bestia.model.dao.AccountDAO;
import bestia.model.domain.Account;
import bestia.zoneserver.actor.zone.ClientMessageActor.RedirectMessage;
import bestia.zoneserver.service.LoginService;

/**
 * This actor will take {@link LoginRequestMessage} and check the validity of
 * the token. If the message has a valid one then the login is granted. There
 * might be also other conditions like a restricted login when the server is in
 * maintenance mode.
 * <p>
 * A {@link DistributedPubSubMediator} is used in order to provide a random
 * cluster wide routing logic for incoming messages.
 * </p>
 * <p>
 * The bestia master is activated also inside this actor to avoid sync issues
 * with the client because the next calls from the client require everything to
 * be activated and ready on the server side. Doing it here as one single unit
 * of operation eases this problem.
 * </p>
 * 
 * @author Thomas Felix
 *
 */
@Component("LoginActor")
@Scope("prototype")
public class LoginAuthActor extends AbstractActor {

	public static final String NAME = "login";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final LoginService loginService;
	private final AccountDAO accountDao;

	@Autowired
	public LoginAuthActor(LoginService loginService,  AccountDAO accountDao) {

		this.loginService = Objects.requireNonNull(loginService);
		this.accountDao = Objects.requireNonNull(accountDao);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(LoginAuthMessage.class, this::handleAuthMessage)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		final RedirectMessage msg = RedirectMessage.get(LoginAuthMessage.class);
		context().parent().tell(msg, getSelf());
	}

	private void handleAuthMessage(LoginAuthMessage msg) {
		LOG.debug("LoginRequestMessage received: {}", msg.toString());

		final long accId = msg.getAccountId();

		if (!loginService.canLogin(accId, msg.getToken())) {

			final LoginAuthReplyMessage response = new LoginAuthReplyMessage(
					accId,
					LoginState.DENIED,
					"");
			getSender().tell(response, getSelf());

		} else {
			
			final Account account = accountDao.findOne(accId);
			final LoginAuthReplyMessage response = new LoginAuthReplyMessage(
					accId,
					LoginState.ACCEPTED,
					account.getName());
			getSender().tell(response, getSelf());
		}

		// Finished work and can stop now.
		getContext().stop(getSelf());
	}
}
