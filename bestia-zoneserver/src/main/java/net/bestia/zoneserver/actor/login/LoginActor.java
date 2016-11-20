package net.bestia.zoneserver.actor.login;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.service.PlayerBestiaService;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.service.ConnectionService;
import net.bestia.zoneserver.service.PlayerEntityService;
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

	private final AccountDAO accountDao;
	private final ServerRuntimeConfiguration config;
	private final PlayerBestiaService playerBestiaService;
	private final PlayerEntityService entityService;
	private final ConnectionService connectionService;

	@Autowired
	public LoginActor(AccountDAO accountDao,
			ServerRuntimeConfiguration config,
			ConnectionService connectionService,
			PlayerEntityService entityService,
			PlayerBestiaService pbService) {
		super(Arrays.asList(LoginAuthMessage.class));

		this.accountDao = Objects.requireNonNull(accountDao);
		this.config = Objects.requireNonNull(config);
		this.connectionService = Objects.requireNonNull(connectionService);
		this.playerBestiaService = Objects.requireNonNull(pbService);
		this.entityService = Objects.requireNonNull(entityService);
	}

	private void respond(LoginAuthMessage msg, LoginState state, Account acc) {
		final LoginAuthReplyMessage response = new LoginAuthReplyMessage(state);

		if (acc != null) {
			response.setAccountId(acc.getId());
		}

		if (state == LoginState.ACCEPTED) {
			// Announce to the cluster that we have a new connected user.
			// Welcome my friend. :)
			spawnEntities(msg);
		}

		// Special case. We can not use the SendClientActor because the
		// connection was not yet registered by the webserver. Only after this
		// trigger message it will be available by the SendClientActor.
		getSender().tell(response, getSelf());
	}

	private void spawnEntities(LoginAuthMessage msg) {
		LOG.debug("Client connected: {}.", msg);

		// Spawn all bestia entities for this account into the world.
		final Set<PlayerBestia> pbs = playerBestiaService
				.getAllBestias(msg.getAccountId());

		final PlayerBestia master = playerBestiaService.getMaster(msg.getAccountId());

		final Set<PlayerBestiaEntity> bestias = pbs
				.parallelStream()
				.map(x -> new PlayerBestiaEntity(x))
				.collect(Collectors.toSet());
		LOG.debug(String.format("Spawning %d player bestias for acc id: %d", bestias.size(), msg.getAccountId()));
		entityService.putPlayerBestiaEntities(bestias);

		// Extract master now again from bestias and get its entity id.
		Optional<PlayerBestiaEntity> masterEntity = bestias.parallelStream()
				.filter(x -> x.getPlayerBestiaId() == master.getId())
				.findAny();
		
		entityService.setActiveEntity(msg.getAccountId(), masterEntity.get().getId());
		
		// Register the sender.
		connectionService.addClient(msg.getAccountId(), getSender().path());
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("LoginRequestMessage received: {}", msg.toString());

		final LoginAuthMessage loginMsg = (LoginAuthMessage) msg;

		if (config.isMaintenanceMode()) {
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
