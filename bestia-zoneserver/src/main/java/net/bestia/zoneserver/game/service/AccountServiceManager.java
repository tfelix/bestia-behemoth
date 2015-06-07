package net.bestia.zoneserver.game.service;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.Zoneserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generates all the needed account services. Please be careful: This factory is not threadsafe. Therefore each thread
 * should have its own service factory.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AccountServiceManager {

	private final static Logger log = LogManager.getLogger(AccountServiceManager.class);

	private final Zoneserver server;

	private final AccountDAO accountDao;

	/**
	 * 
	 * @param locator
	 * @param server
	 */
	public AccountServiceManager(AccountDAO accountDao, Zoneserver server) {
		if (accountDao == null) {
			throw new IllegalArgumentException("AccDAO can not be null.");
		}
		if (server == null) {
			throw new IllegalArgumentException("Server can not be null.");
		}
		this.accountDao = accountDao;
		this.server = server;
	}

	/**
	 * Returns an Account with the given id. Throws an exception if the account was not found.
	 * 
	 * @param accId
	 * @return
	 */
	public AccountService getAccountService(long accId) {
		Account data = accountDao.find(accId);
		if (data == null) {
			log.warn("No account found. ID: {}", accId);
			throw new IllegalArgumentException("No account found");
		}
		return new AccountService(data, server);
	}

	public AccountService getAccountService(String accEmail) {
		Account data = accountDao.findByEmail(accEmail);
		if (data == null) {
			log.warn("No account found. Identifier: {}", accEmail);
			throw new IllegalArgumentException("No account found");
		}
		return new AccountService(data, server);
	}

	/**
	 * Creates a completely new account.
	 * TODO Das noch implementieren. Hier muss auch die Business Logik rein mit ausgewählter bestia usw.
	 * @param email
	 * @param mastername
	 * @param password
	 * @return
	 */
	public AccountService createNewAccount(String email, String mastername, String password) {
		return null;
	}

}
