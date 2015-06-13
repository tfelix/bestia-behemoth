package net.bestia.model.service;

import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generates all the needed account services. Please be careful: This factory is not threadsafe. Therefore each thread
 * should have its own service factory.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AccountServiceManager extends Service {

	private final static Logger log = LogManager.getLogger(AccountServiceManager.class);


	private final AccountDAO accountDao;

	/**
	 * 
	 * @param locator
	 * @param server
	 */
	public AccountServiceManager(AccountDAO accountDao, MessageSender sender) {
		super(sender);
		if (accountDao == null) {
			throw new IllegalArgumentException("AccDAO can not be null.");
		}
		
		this.accountDao = accountDao;
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
		return new AccountService(data, sender);
	}

	public AccountService getAccountService(String accEmail) {
		Account data = accountDao.findByEmail(accEmail);
		if (data == null) {
			log.warn("No account found. Identifier: {}", accEmail);
			throw new IllegalArgumentException("No account found");
		}
		return new AccountService(data, sender);
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

	@Override
	protected Message getDataChangedMessage() {
		// TODO Auto-generated method stub
		return null;
	}

}
