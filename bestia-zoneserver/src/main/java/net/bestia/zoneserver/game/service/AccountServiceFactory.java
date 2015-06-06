package net.bestia.zoneserver.game.service;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.Zoneserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generates all the needed account services. Please be careful:
 * This factory is not threadsafe. Therefore each thread should
 * have its own service factory.
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AccountServiceFactory {
	
	private final static Logger log = LogManager.getLogger(AccountServiceFactory.class);
	
	private AccountDAO accDAO;
	private final Zoneserver server;
	
	/**
	 * Ctor.
	 * @param accDAO AccountDAO for interacting with the database.
	 * @param messageQueue Queue to generate messages for the messaging subsystem.
	 */
	public AccountServiceFactory(AccountDAO accDAO,
			Zoneserver server) {
		if(accDAO == null) {
			throw new IllegalArgumentException("AccDAO can not be null.");
		}
		if(server == null) {
			throw new IllegalArgumentException("Server can not be null.");
		}
		this.accDAO = accDAO;
		this.server = server;
	}

	/**
	 * Returns an Account with the given id. Throws an exception if
	 * the account was not found.
	 * @param accId
	 * @return
	 */
	public AccountService getAccount(long accId) {
		Account data = accDAO.find(accId);
		return new AccountService(data, server);
	}
	
	public AccountService getAccountByName(String accName) {
		Account data = accDAO.findByEmail(accName);
		if(data == null) {
			log.warn("No account found. Identifier: {}", accName);
			throw new IllegalArgumentException("No account found");
		}
		return new AccountService(data, server);
	}
	
	/**
	 * Creates a completely new account.
	 * 
	 * @param email
	 * @param mastername
	 * @param password
	 * @return
	 */
	public AccountService createNewAccount(String email, String mastername, String password) {
		return null;
	}
	
	
}
