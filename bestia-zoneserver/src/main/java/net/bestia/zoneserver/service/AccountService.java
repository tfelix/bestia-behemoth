package net.bestia.zoneserver.service;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.PlayerClass;

/**
 * Generates all the needed account services. Please be careful: This factory is
 * not threadsafe. Therefore each thread should have its own AccountService.
 * 
 * @author Thomas Felix
 *
 */
@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
@Service
public class AccountService {

	private static final Logger LOG = LoggerFactory.getLogger(AccountService.class);

	private AccountDAO accountDao;
	private PlayerBestiaDAO playerBestiaDao;
	private BestiaDAO bestiaDao;
	private ConnectionService connectionService;

	@Autowired
	public AccountService(AccountDAO accDao, PlayerBestiaDAO playerBestiaDao, BestiaDAO bestiaDao,
			ConnectionService connectionService) {
		this.accountDao = Objects.requireNonNull(accDao);
		this.bestiaDao = Objects.requireNonNull(bestiaDao);
		this.playerBestiaDao = Objects.requireNonNull(playerBestiaDao);
		this.connectionService = Objects.requireNonNull(connectionService);
	}

	/**
	 * This will return a {@link Account} with the needed, new access token. If
	 * the wrong credentials where provided null is returned instead.
	 * 
	 * @param accName
	 * @return The account with the new token, or null if wrong credentials.
	 */
	public Account createLoginToken(String accName, String password) {
		final Account acc = accountDao.findByEmail(accName);
		if (acc == null) {
			return null;
		}

		if (!acc.getPassword().matches(password)) {
			return null;
		}

		acc.setLastLogin(new Date());
		acc.setLoginToken(UUID.randomUUID().toString());
		accountDao.save(acc);
		return acc;
	}

	/**
	 * Creates a completely new account. The username will be given to the
	 * bestia master. No other bestia mastia can have this name.
	 * 
	 * @param email
	 *            E-Mail to use.
	 * @param mastername
	 *            Username of the bestia master.
	 * @param password
	 *            Password for the account.
	 * @param starter
	 *            Choosen starter bestia.
	 * @return {@code TRUE} if the new account coule be created. {@code FALSE}
	 *         otherwise.
	 */
	public void createNewAccount(String email, String mastername, String password, PlayerClass starter) {
		if (mastername == null || mastername.isEmpty()) {
			throw new IllegalArgumentException("Mastername can not be null or empty.");
		}

		final Account account = new Account(email, password);

		// TODO das hier noch auslagern. Die aktivierung soll nur per
		// username/password anmeldung notwendig sein.
		account.setActivated(true);

		// TODO Starter ID durch ein Script ? bestimmen lassen. Außerdem
		// Eventcodes berücksichtigen.
		int starterId = 1;

		// Depending on the master get the offspring bestia.
		final Bestia origin = bestiaDao.findOne(starterId);
		if (origin == null) {
			LOG.error("Starter bestia with id {} could not been found.", starterId);
			throw new IllegalArgumentException("Starter bestia was not found.");
		}

		// Check if there is a bestia master with this name.
		final PlayerBestia existingMaster = playerBestiaDao.findMasterBestiaWithName(mastername);

		if (existingMaster != null) {
			LOG.warn("Can not create account. Master name already exists: {}", mastername);
			throw new IllegalArgumentException("A master with this name does already exist.");
		}

		// Create the bestia.
		final PlayerBestia masterBestia = new PlayerBestia(account, origin, BaseValues.getStarterIndividualValues());

		masterBestia.setName(mastername);
		masterBestia.setMaster(account);

		// Generate ID.
		try {
			accountDao.save(account);
			playerBestiaDao.save(masterBestia);

		} catch (JpaSystemException ex) {
			LOG.warn("Could not create account because of duplicate mail: {}", ex.getMessage(), ex);
			throw new IllegalArgumentException("Could not create account. Duplicate mail.", ex);
		}
	}

	/**
	 * Returns accounts via their username (bestia master name), but only if
	 * they are online. If the account is currently not logged in then null is
	 * returned.
	 * 
	 * @param username
	 *            The bestia master name to look for.
	 * @return The {@link Account} of this bestia master or null if the name
	 *         does not exist or the account is not online.
	 */
	public Account getOnlineAccountByName(String username) {
		Objects.requireNonNull(username);
		final Account acc = accountDao.findByUsername(username);

		if (acc == null) {
			return null;
		}

		// Check if this account is online.
		if (null == connectionService.getPath(acc.getId())) {
			return null;
		}

		return acc;
	}

}
