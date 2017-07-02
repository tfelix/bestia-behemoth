package net.bestia.zoneserver.service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import akka.actor.ActorRef;
import net.bestia.entity.Entity;
import net.bestia.entity.PlayerBestiaEntityFactory;
import net.bestia.entity.PlayerEntityService;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.messages.web.AccountLoginToken;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.configuration.MaintenanceLevel;
import net.bestia.zoneserver.configuration.RuntimeConfigService;

/**
 * Performs login or logout of the bestia server system.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class LoginService {

	private static final Logger LOG = LoggerFactory.getLogger(LoginService.class);

	private final RuntimeConfigService config;
	private final AccountDAO accountDao;
	private final ConnectionService connectionService;
	private final PlayerEntityService playerEntityService;
	private final PlayerBestiaService playerBestiaService;
	private final ZoneAkkaApi akkaApi;
	private final PlayerBestiaEntityFactory playerEntityFactory;

	@Autowired
	public LoginService(RuntimeConfigService config,
			AccountDAO accountDao,
			PlayerEntityService playerEntityService,
			ConnectionService connectionService,
			PlayerBestiaService playerBestiaService,
			ZoneAkkaApi akkaApi,
			PlayerBestiaEntityFactory playerEntityFactory) {

		this.config = Objects.requireNonNull(config);
		this.accountDao = Objects.requireNonNull(accountDao);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
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
		if (accId < 0) {
			throw new IllegalArgumentException("Account ID must be positive.");
		}
		Objects.requireNonNull(connectionRef);

		final Account account = accountDao.findOne(accId);

		if (account == null) {
			LOG.warn("Account {} was not found.", accId);
			return;
		}

		// First register the sender connection.
		connectionService.addClient(accId, connectionRef.path());

		// Spawn all bestia entities for this account into the world.
		final PlayerBestia master = playerBestiaService.getMaster(accId);

		LOG.debug(String.format("Login in acc: %d. Spawning master bestias.", accId));

		final Entity masterEntity = playerEntityFactory.build(master);

		try {
			// Save the entity.
			// Now activate the master and notify the client.
			playerEntityService.putPlayerEntity(masterEntity);
			playerEntityService.setActiveEntity(accId, masterEntity.getId());
		} catch (IllegalArgumentException e) {
			// Seems the entity has no player component. Aborting.
			LOG.warn("Could not login because of exception.", e);
			final LoginAuthReplyMessage response = new LoginAuthReplyMessage(
					accId,
					LoginState.DENIED,
					account.getName());
			akkaApi.sendToClient(response);
			return;
		}

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
		if (accId < 0) {
			throw new IllegalArgumentException("Account ID must be positive.");
		}

		final Account acc = accountDao.findOne(accId);

		if (acc == null) {
			LOG.warn("Can not logout account id: %d. ID does not exist.", accId);
			return;
		}

		// Unregister connection.
		LOG.debug("Logout acc id: {}.", accId);
		connectionService.removeClient(accId);

		final Set<Entity> playerEntities = playerEntityService.getPlayerEntities(accId);

		playerEntities.forEach(playerEntityService::save);
		playerEntityService.removePlayerBestias(accId);
		
		// Recycle all entities.
	}

	/**
	 * Logs out all users from the server.
	 */
	public void logoutAll() {
		Set<Long> ids = connectionService.getAllConnectedAccountIds();
		ids.forEach(this::logout);
	}

	/**
	 * Logs out all users who are blow the given user level.
	 * 
	 * @param level
	 */
	public void logoutAllUsersBelow(UserLevel level) {
		connectionService.getAllConnectedAccountIds().stream().filter(accId -> {
			return accountDao.findOne(accId).getUserLevel().compareTo(level) == -1;
		}).forEach(this::logout);
	}

	public AccountLoginToken setNewLoginToken(String username, String password) {
		Objects.requireNonNull(username);
		Objects.requireNonNull(password);

		LOG.debug("Trying to set login token for username {}.", username);

		final Account account = accountDao.findByUsername(username);

		if (account == null) {
			LOG.debug("Account with username {} not found.", username);
			return null;
		}

		if (!account.getPassword().matches(password)) {
			LOG.debug("Password does not match with username {}.", username);
			return null;
		}

		final String uuid = UUID.randomUUID().toString();
		account.setLoginToken(uuid);

		// Save to database.
		accountDao.save(account);

		// Check login.
		final AccountLoginToken answerToken = new AccountLoginToken(
				account.getId(),
				account.getName(),
				uuid);

		return answerToken;
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

		if (config.getMaintenanceMode() != MaintenanceLevel.NONE) {

			// Depending on maintenance mode certain users can login.
			if (config.getMaintenanceMode() == MaintenanceLevel.FULL) {
				LOG.debug("No accounts can login during full maintenance.");
				return false;
			}

			if (config.getMaintenanceMode() == MaintenanceLevel.PARTIAL
					&& acc.getUserLevel().compareTo(UserLevel.SUPER_GM) < 0) {
				LOG.debug("Account {} can not login during maintenance User level too low.", accId);
				return false;
			}
		}

		LOG.trace("Account {} login permitted.", accId);
		return true;
	}
}
