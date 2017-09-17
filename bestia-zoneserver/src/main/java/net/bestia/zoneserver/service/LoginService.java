package net.bestia.zoneserver.service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import akka.actor.Address;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.PlayerBestiaEntityFactory;
import net.bestia.entity.PlayerEntityService;
import net.bestia.messages.login.LogoutMessage;
import net.bestia.messages.web.AccountLoginRequest;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.server.MaintenanceLevel;
import net.bestia.zoneserver.actor.connection.ClientConnectionActor;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;
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
	private final EntityService entityService;

	@Autowired
	public LoginService(RuntimeConfigService config,
			AccountDAO accountDao,
			PlayerEntityService playerEntityService,
			ConnectionService connectionService,
			PlayerBestiaService playerBestiaService,
			ZoneAkkaApi akkaApi,
			PlayerBestiaEntityFactory playerEntityFactory,
			EntityService entityService) {

		this.config = Objects.requireNonNull(config);
		this.accountDao = Objects.requireNonNull(accountDao);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.connectionService = Objects.requireNonNull(connectionService);
		this.playerBestiaService = Objects.requireNonNull(playerBestiaService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
		this.playerEntityFactory = Objects.requireNonNull(playerEntityFactory);
		this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * Performs a login for this account. This prepares the bestia server system
	 * for upcoming commands from this player. The player bestia entity is
	 * spawned on the server.
	 * 
	 * @param accId
	 *            The account id to perform a login.
	 * @return The logged in Account or NULL if login failed.
	 */
	public Account login(long accId) {
		if (accId < 0) {
			throw new IllegalArgumentException("Account ID must be positive.");
		}

		final Account account = accountDao.findOne(accId);

		if (account == null) {
			LOG.warn("Account {} was not found.", accId);
			return null;
		}

		// TODO Das hier ist ein fehler. Erst den client complett anmelden und
		// danach beginnen die entities zu instanzieren.

		// Spawn all bestia entities for this account into the world.
		final PlayerBestia master = playerBestiaService.getMaster(accId);

		Entity masterEntity = null;

		if (master.getEntityId() != 0) {
			LOG.debug("Login in acc: {}. Master bestia already spawned (eid: {}). Using it", accId,
					master.getEntityId());
			masterEntity = entityService.getEntity(master.getEntityId());

			if (masterEntity == null) {
				LOG.warn("Master entity {} for account {} not found even though ID was set. Spawning it.",
						master.getEntityId(), accId);
				masterEntity = playerEntityFactory.build(master);
			}

		} else {
			LOG.debug("Login in acc: {}. Spawning master bestias.", accId);
			masterEntity = playerEntityFactory.build(master);
		}

		try {
			// Save the entity.
			// Now activate the master and notify the client.
			playerEntityService.putPlayerEntity(masterEntity);
			playerEntityService.setActiveEntity(accId, masterEntity.getId());
		} catch (IllegalArgumentException e) {
			// Seems the entity has no player component. Aborting.
			LOG.warn("Could not login because of exception.", e);
			return null;
		}
		
		return account;
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

		// Send disconnect message to the webserver.
		// Depending on the logout state the actor might have already been
		// stopped.
		final LogoutMessage logoutMsg = new LogoutMessage(accId);
		akkaApi.sendToClient(logoutMsg);
		akkaApi.sendToActor(ClientConnectionActor.getActorName(accId), logoutMsg);

		// Unregister connection.
		LOG.debug("Logout acc id: {}.", accId);

		final Set<Entity> playerEntities = playerEntityService.getPlayerEntities(accId);

		try {
			playerEntities.forEach(playerEntityService::save);
		} catch (Exception e) {
			LOG.warn("Something went wrong saving entities.", e);
		}

		playerEntityService.removePlayerBestias(accId);

		// Recycle all entities.
		playerEntities.forEach(entityService::delete);
	}

	public void logoutAllFromServer(Address address) {

	}

	/**
	 * Logs out all users who are blow the given user level.
	 * 
	 * @param level
	 */
	public void logoutAllUsersBelow(UserLevel level) {
		connectionService.getAllConnectedAccountIds().forEachRemaining(accId -> {
			final Account acc = accountDao.findOne(accId);

			if (acc.getUserLevel().compareTo(level) == -1) {
				logout(accId);
			}
		});
	}

	public AccountLoginRequest setNewLoginToken(AccountLoginRequest request) {
		Objects.requireNonNull(request);

		LOG.debug("Trying to set login token for username {}.", request);

		final Account account = accountDao.findByUsernameOrEmail(request.getUsername());

		if (account == null) {
			LOG.debug("Account with username {} not found.", request.getUsername());
			return request.fail();
		}

		if (!account.getPassword().matches(request.getPassword())) {
			LOG.debug("Password does not match: {}.", request);
			return request.fail();
		}

		// Create new token and save it.
		final String uuid = UUID.randomUUID().toString();
		account.setLoginToken(uuid);
		accountDao.save(account);

		// Check login.
		return request.success(account.getId(), uuid);
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

		if (acc.getLoginToken().isEmpty()) {
			LOG.debug("Login with empty token is not allowed.");
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

	public void logoutAll() {
		// TODO Auto-generated method stub

	}
}
