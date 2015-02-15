package net.bestia.core.game.service;

import net.bestia.core.game.model.Account;
import net.bestia.core.net.Messenger;
import net.bestia.core.persist.AccountDAO;

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
	private final Messenger messenger;
	
	/**
	 * Ctor.
	 * @param accDAO AccountDAO for interacting with the database.
	 * @param messageQueue Queue to generate messages for the messaging subsystem.
	 */
	public AccountServiceFactory(AccountDAO accDAO,
			Messenger messenger) {
		if(accDAO == null) {
			throw new IllegalArgumentException("AccDAO can not be null.");
		}
		if(messenger == null) {
			throw new IllegalArgumentException("Messenger can not be null.");
		}
		this.accDAO = accDAO;
		this.messenger = messenger;
	}

	/**
	 * Returns an Account with the given id. Throws an exception if
	 * the account was not found.
	 * @param accId
	 * @return
	 */
	public AccountService getAccount(int accId) {
		Account data = accDAO.findByID(Account.class, accId);
		return new AccountService(data, messenger);
	}
	
	public AccountService getAccountByName(String accName) {
		Account data = accDAO.getByIdentifier(accName);
		if(data == null) {
			log.warn("No account found. Identifier: {}", accName);
			throw new IllegalArgumentException("No account found");
		}
		return new AccountService(data, messenger);
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
