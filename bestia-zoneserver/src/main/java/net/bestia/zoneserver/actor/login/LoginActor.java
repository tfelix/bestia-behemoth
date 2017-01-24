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
import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.service.PlayerBestiaService;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerEntity;
import net.bestia.zoneserver.service.ConnectionService;
import net.bestia.zoneserver.service.PlayerEntityService;
import net.bestia.zoneserver.service.RuntimeConfigurationService;

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
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component("LoginActor")
@Scope("prototype")
public class LoginActor extends BestiaRoutingActor {

	public static final String NAME = "login";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final AccountDAO accountDao;
	private final RuntimeConfigurationService config;
	private final PlayerBestiaService playerBestiaService;
	private final PlayerEntityService entityService;
	private final ConnectionService connectionService;

	/**
	 * Ctor.
	 * 
	 * @param accountDao
	 * @param config
	 * @param connectionService
	 * @param entityService
	 * @param pbService
	 */
	@Autowired
	public LoginActor(AccountDAO accountDao,
			RuntimeConfigurationService config,
			ConnectionService connectionService,
			PlayerEntityService entityService,
			PlayerBestiaService pbService) {
		super(Arrays.asList(LoginAuthMessage.class));

		setChildRouting(false);

		this.accountDao = Objects.requireNonNull(accountDao);
		this.config = Objects.requireNonNull(config);
		this.connectionService = Objects.requireNonNull(connectionService);
		this.playerBestiaService = Objects.requireNonNull(pbService);
		this.entityService = Objects.requireNonNull(entityService);
	}

	private void respond(LoginAuthMessage msg, LoginState state, Account acc) {

		// Special case. We can not use the SendClientActor because the
		// connection was not yet registered by the webserver. (If there is no
		// successfull login we cant even register). Only after this
		// trigger message it will be available by the SendClientActor.

		if (state == LoginState.ACCEPTED) {
			// Announce to the cluster that we have a new connected user.
			// Welcome my friend. :)
			final PlayerEntity master = spawnEntities(msg);

			// Now activate the master.
			entityService.setActiveEntity(master.getAccountId(), master.getId());

			final LoginAuthReplyMessage response = new LoginAuthReplyMessage(acc.getId(), state, acc.getName());
			LOG.debug("Sending: {}.", response.toString());
			getSender().tell(response, getSelf());

			final BestiaActivateMessage activateMsg = new BestiaActivateMessage(msg.getAccountId(),
					master.getPlayerBestiaId());
			getSender().tell(activateMsg, getSelf());
			LOG.debug("Sending: {}.", activateMsg.toString());

		} else {
			final LoginAuthReplyMessage response = new LoginAuthReplyMessage(state, "");
			if (acc != null) {
				response.setAccountId(acc.getId());
			}
			getSender().tell(response, getSelf());
		}
	}

	/**
	 * Creates the entities (bestias) of the player. It will return the master
	 * bestia of the player.
	 */
	private PlayerEntity spawnEntities(LoginAuthMessage msg) {
		LOG.debug("Client connected: {}.", msg);

		// Spawn all bestia entities for this account into the world.
		final Set<PlayerBestia> pbs = playerBestiaService.getAllBestias(msg.getAccountId());

		final PlayerBestia master = playerBestiaService.getMaster(msg.getAccountId());

		final Set<PlayerEntity> bestias = pbs
				.parallelStream()
				.map(x -> new PlayerEntity(msg.getAccountId(), x))
				.collect(Collectors.toSet());
		LOG.debug(String.format("Spawning %d player bestias for acc id: %d", bestias.size(), msg.getAccountId()));
		entityService.putPlayerEntities(bestias);

		// Extract master now again from bestias and get its entity id.
		final Optional<PlayerEntity> masterEntity = bestias.parallelStream()
				.filter(x -> x.getPlayerBestiaId() == master.getId())
				.findAny();

		// Register the sender connection.
		connectionService.addClient(msg.getAccountId(), getSender().path());

		return masterEntity.get();
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
