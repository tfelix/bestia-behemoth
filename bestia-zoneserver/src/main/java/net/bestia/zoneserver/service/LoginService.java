package net.bestia.zoneserver.service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import akka.actor.ActorRef;
import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.configuration.RuntimeConfigurationService;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityServiceContext;
import net.bestia.zoneserver.entity.PlayerEntityService;

/**
 * Performs login or logout of the bestia server system.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class LoginService {

	private static final Logger LOG = LoggerFactory.getLogger(LoginService.class);

	private final RuntimeConfigurationService config;
	private final AccountDAO accountDao;
	private final ConnectionService connectionService;
	private final EntityServiceContext entityServiceCtx;
	private final PlayerBestiaService playerBestiaService;
	private final ZoneAkkaApi akkaApi;

	@Autowired
	public LoginService(RuntimeConfigurationService config,
			AccountDAO accountDao,
			EntityServiceContext entityServiceCtx,
			ConnectionService connectionService,
			PlayerBestiaService playerBestiaService,
			ZoneAkkaApi akkaApi) {

		this.config = Objects.requireNonNull(config);
		this.accountDao = Objects.requireNonNull(accountDao);
		this.entityServiceCtx = Objects.requireNonNull(entityServiceCtx);
		this.connectionService = Objects.requireNonNull(connectionService);
		this.playerBestiaService = Objects.requireNonNull(playerBestiaService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	/**
	 * Performs a login for this account. This prepares the bestia server system
	 * for upcoming commands from this player. The player bestia entity is
	 * spawned on the server.
	 * 
	 * @param accId
	 *            The account id to perform a login.
	 * @return The player master entity.
	 */
	public void login(long accId, ActorRef connectionRef) {

		// First register the sender connection.
		connectionService.addClient(accId, connectionRef.path());

		final Account account = accountDao.findOne(accId);

		// Spawn all bestia entities for this account into the world.
		final PlayerBestia master = playerBestiaService.getMaster(accId);

		LOG.debug(String.format("Login in acc: {}. Spawning master bestias.", accId));

		Entity masterEntity;
		entityServiceCtx.getPlayer().putPlayerEntity(masterEntity);

		// Set the position in order to send updates to the client.

		for (Entity pb : bestias) {
			final Point p = pb.getPosition();
			pb.setPosition(p.getX(), p.getY());
		}

		// Extract master now again from bestias and get its entity id.

		final Optional<Entity> masterEntity = pbs.stream()
				.filter(x -> x.getPlayerBestiaId() == master.getId())
				.findAny();

		if (!masterEntity.isPresent()) {
			LOG.error("Account {} has no bestia master! Aborting login process.",
					accId);
			logout(accId);
			throw new IllegalStateException("Account has no bestia master. Aborting.");
		}

		// Now activate the master and notify the client.
		entityServiceCtx.getPlayer().setActiveEntity(accId, masterEntity.getId());

		final BestiaActivateMessage activateMsg = new BestiaActivateMessage(
				accId,
				master.getId());
		akkaApi.sendToClient(activateMsg);

		final LoginAuthReplyMessage response = new LoginAuthReplyMessage(
				accId,
				LoginState.ACCEPTED,
				account.getName());
		akkaApi.sendToClient(response);
	}

	/**
	 * Logouts a player. Also cleans up all the data and persists it back to the
	 * database.
	 * 
	 * @param accId
	 *            The account id to logout.
	 */
	public void logout(long accId) {

		final Account acc = accountDao.findOne(accId);

		if (acc == null) {
			LOG.error("Can not logout account id: %d. ID does not exist.", accId);
			return;
		}

		// Unregister.
		LOG.debug("Logout acc id: {}.", accId);
		connectionService.removeClient(accId);

		final Set<Entity> bestias = playerEntityService.getPlayerEntities(accId);
		playerBestiaService.updatePlayerBestias(bestias);
		playerEntityService.removePlayerBestias(accId);
	}

	/**
	 * An user can only login if he provides the correct login token and the
	 * server is not in maintenance mode. A game master can override the server
	 * maintenance mode flag.
	 * 
	 * @param accId
	 *            The account ID to check if the login.
	 * @param token
	 *            The token of this account id.
	 * @return TRUE if the account is permitted to login FALSE otherwise.
	 */
	public boolean canLogin(long accId, String token) {

		LOG.debug("Checking login for account {}.", accId);

		final Account acc = accountDao.findOne(accId);

		if (acc == null) {
			LOG.trace("No account with id {} found.", accId);
			return false;
		}

		if (!acc.getLoginToken().equals(token)) {
			LOG.trace("Account {} logintoken does not match.", accId);
			return false;
		}

		if (config.isMaintenanceMode() && acc.getUserLevel().compareTo(UserLevel.SUPER_GM) < 0) {
			LOG.trace("Account {} can not login during maintenance.", accId);
			return false;
		}

		LOG.trace("Account {} login permitted.", accId);
		return true;
	}

}
