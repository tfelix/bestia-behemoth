package net.bestia.core.game.service;

import java.util.concurrent.BlockingQueue;

import net.bestia.core.game.model.Account;
import net.bestia.core.message.Message;
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
public final class AccountServiceFactory {
	
	private final static Logger log = LogManager.getLogger(AccountServiceFactory.class);

	private BlockingQueue<Message> messageQueue;	
	private AccountDAO accDAO;
	
	/**
	 * Ctor.
	 * @param accDAO AccountDAO for interacting with the database.
	 * @param messageQueue Queue to generate messages for the messaging subsystem.
	 */
	public AccountServiceFactory(AccountDAO accDAO,
			BlockingQueue<Message> messageQueue) {
		if(accDAO == null) {
			throw new IllegalArgumentException("AccDAO can not be null.");
		}
		if(messageQueue == null) {
			throw new IllegalArgumentException("messageQueue can not be null.");
		}
		this.accDAO = accDAO;
		this.messageQueue = messageQueue;
	}

	/**
	 * Returns an Account with the given id. Throws an exception if
	 * the account was not found.
	 * @param accId
	 * @return
	 */
	public AccountService getAccount(int accId) {
		Account data = accDAO.findByID(Account.class, accId);
		return new AccountService(data, messageQueue);
	}
	
	public AccountService getAccountByName(String accName) {
		Account data = accDAO.getByIdentifier(accName);
		if(data == null) {
			log.warn("No account found. Identifier: {}", accName);
			throw new IllegalArgumentException("No account found");
		}
		return new AccountService(data, messageQueue);
	}
	
	/**
	 * Creates a completly new account.
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
