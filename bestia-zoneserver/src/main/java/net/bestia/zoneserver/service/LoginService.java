package net.bestia.zoneserver.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import akka.actor.ActorRef;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.configuration.RuntimeConfigurationService;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityServiceContext;
import net.bestia.zoneserver.entity.PlayerBestiaEntityFactory;

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
	private final PlayerBestiaEntityFactory playerEntityFactory;

	@Autowired
	public LoginService(RuntimeConfigurationService config,
			AccountDAO accountDao,
			EntityServiceContext entityServiceCtx,
			ConnectionService connectionService,
			PlayerBestiaService playerBestiaService,
			ZoneAkkaApi akkaApi,
			PlayerBestiaEntityFactory playerEntityFactory) {

		this.config = Objects.requireNonNull(config);
		this.accountDao = Objects.requireNonNull(accountDao);
		this.entityServiceCtx = Objects.requireNonNull(entityServiceCtx);
		this.connectionService = Objects.requireNonNull(connectionService);
		this.playerBestiaService = Objects.requireNonNull(playerBestiaService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
		this.playerEntityFactory = Objects.requireNonNull(playerEntityFactory);
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
		if(accId < 0) {
			throw new IllegalArgumentException("Account ID must be positive.");
		}
		Objects.requireNonNull(connectionRef);
		
		final Account account = accountDao.findOne(accId);
		
		if(account == null) {
			LOG.warn("Account {} was not found.", accId);
			return;
		}

		// First register the sender connection.
		connectionService.addClient(accId, connectionRef.path());

		// Spawn all bestia entities for this account into the world.
		final PlayerBestia master = playerBestiaService.getMaster(accId);

		LOG.debug(String.format("Login in acc: %d. Spawning master bestias.", accId));
		
		final Entity masterEntity = playerEntityFactory.build(master);
		
		// Save the entity.
		entityServiceCtx.getPlayer().putPlayerEntity(masterEntity);

		// Now activate the master and notify the client.
		entityServiceCtx.getPlayer().setActiveEntity(accId, masterEntity.getId());

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
		if(accId < 0) {
			throw new IllegalArgumentException("Account ID must be positive.");
		}

		final Account acc = accountDao.findOne(accId);

		if (acc == null) {
			LOG.error("Can not logout account id: %d. ID does not exist.", accId);
			return;
		}

		// Unregister connection.
		LOG.debug("Logout acc id: {}.", accId);
		connectionService.removeClient(accId);

		entityServiceCtx.getPlayer().getMasterEntity(accId).ifPresent(master -> {
			playerBestiaService.updatePlayerBestias(master);
			entityServiceCtx.getPlayer().removePlayerBestia(master);
		});
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
		Objects.requireNonNull(token);

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
