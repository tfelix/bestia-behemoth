package net.bestia.zoneserver.actor.login;

import java.util.Objects;

import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;

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
public class LoginActor extends UntypedActor {

	final private LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final AccountDAO accountDao;

	public LoginActor(AccountDAO accountDao) {
		this.accountDao = Objects.requireNonNull(accountDao);

		// Setup the mediator.
		final ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();

		mediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());
	}

	public static Props props(final AccountDAO accountDao) {
		// Props must be deployed locally since we contain a dao (non
		// serializable)
		return Props.create(new Creator<LoginActor>() {
			private static final long serialVersionUID = 1L;

			public LoginActor create() throws Exception {
				return new LoginActor(accountDao);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (!(message instanceof LoginAuthMessage)) {
			unhandled(message);
			return;
		}

		LOG.debug("LoginRequestMessage received: {}", message.toString());

		// TODO Check if we are in maintenance mode.

		final LoginAuthMessage msg = (LoginAuthMessage) message;

		// Check to see if the find the requested account.
		final Account acc = accountDao.findOne(msg.getAccountId());

		if (acc == null) {
			respond(msg, LoginState.DENIED);
			return;
		}

		if (acc.getLoginToken().equals(msg.getToken())) {
			respond(msg, LoginState.ACCEPTED);
		} else {
			respond(msg, LoginState.DENIED);
		}
	}

	private void respond(LoginAuthMessage msg, LoginState state) {
		final LoginAuthReplyMessage response = new LoginAuthReplyMessage(state);
		getSender().tell(response, getSelf());
	}

}
