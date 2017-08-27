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

import net.bestia.messages.web.AccountRegistration;
import net.bestia.messages.web.AccountRegistrationError;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.Password;
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

	private int getIdFromPlayerClass(PlayerClass pc) {
		return 1;
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
		
		// Dont allow login for not activated accounts.
		if(!acc.isActivated()) {
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
	 * Checks if an account is not activated yet and then it will check if the
	 * activation code matches the login token and if yes activate the account.
	 * 
	 * @param activationCode
	 *            The activation code for this account.
	 * @return TRUE if successfull. FALSE otherwise.
	 */
	public boolean activateAccount(long accId, String activationCode) {

		final Account acc = accountDao.findOne(accId);
		
		// Abort if account is activated.
		if(acc.isActivated()) {
			return false;
		}
		
		if(acc.getLoginToken().equals(activationCode)) {
			acc.setActivated(true);
			acc.setLoginToken("");
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Creates a completely new account. The username will be given to the
	 * bestia master. No other bestia mastia can have this name.
	 * 
	 * @return {@code TRUE} if the new account coule be created. {@code FALSE}
	 *         otherwise.
	 */
	public AccountRegistrationError createNewAccount(AccountRegistration data) {

		final String mastername = data.getUsername();

		if (mastername == null || mastername.isEmpty()) {
			throw new IllegalArgumentException("Mastername can not be null or empty.");
		}

		final Account account = new Account(data.getEmail(), data.getPassword());

		// TODO das hier noch auslagern. Die aktivierung soll nur per
		// username/password anmeldung notwendig sein.
		account.setActivated(true);

		// Depending on the master get the offspring bestia.
		final int starterId = getIdFromPlayerClass(data.getPlayerClass());
		final Bestia origin = bestiaDao.findOne(starterId);

		if (origin == null) {
			LOG.error("Starter bestia with id {} could not been found.", starterId);
			throw new IllegalArgumentException("Starter bestia was not found.");
		}

		if (accountDao.findByEmail(data.getEmail()) != null) {
			LOG.debug("Could not create account because of duplicate mail: {}", data.getEmail());
			return AccountRegistrationError.EMAIL_INVALID;
		}

		// Check if there is a bestia master with this name.
		final PlayerBestia existingMaster = playerBestiaDao.findMasterBestiaWithName(mastername);

		if (existingMaster != null) {
			LOG.warn("Can not create account. Master name already exists: {}", mastername);
			return AccountRegistrationError.USERNAME_INVALID;
		}

		// Create the bestia.
		final PlayerBestia masterBestia = new PlayerBestia(account, origin, BaseValues.getStarterIndividualValues());

		masterBestia.setName(mastername);
		masterBestia.setMaster(account);

		// Generate ID.
		try {

			accountDao.save(account);
			playerBestiaDao.save(masterBestia);

			return AccountRegistrationError.NONE;

		} catch (JpaSystemException ex) {
			LOG.warn("Could not create account: {}", ex.getMessage(), ex);
			return AccountRegistrationError.GENERAL_ERROR;
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
		if (connectionService.isConnected(acc.getId())) {
			return null;
		}

		return acc;
	}

	/**
	 * Sets the password without checking the old password first.
	 * 
	 * @param accountName
	 * @param newPassword
	 * @return
	 */
	public boolean changePasswordWithoutCheck(String accountName, String newPassword) {
		Objects.requireNonNull(accountName);
		Objects.requireNonNull(newPassword);

		final Account acc = accountDao.findByUsernameOrEmail(accountName);

		if (acc == null) {
			return false;
		}

		acc.setPassword(new Password(newPassword));
		accountDao.save(acc);
		return true;
	}

	/**
	 * Tries to change the password for the given account. The old password must
	 * match first before this method executes.
	 * 
	 * @param data
	 * @return
	 */
	public boolean changePassword(String accountName, String oldPassword, String newPassword) {
		Objects.requireNonNull(accountName);
		Objects.requireNonNull(oldPassword);
		Objects.requireNonNull(newPassword);

		if (newPassword.isEmpty()) {
			return false;
		}

		final Account acc = accountDao.findByUsernameOrEmail(accountName);

		if (acc == null) {
			return false;
		}

		final Password password = acc.getPassword();

		if (!password.matches(oldPassword)) {
			return false;
		}

		acc.setPassword(new Password(newPassword));
		accountDao.save(acc);
		return true;
	}

}
