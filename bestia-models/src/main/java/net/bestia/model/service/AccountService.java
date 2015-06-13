package net.bestia.model.service;

import net.bestia.messages.Message;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Password;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the business logic of the Account.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AccountService extends Service {

	private static final Logger log = LogManager
			.getLogger(AccountService.class);

	public final static int MAX_MASTER_LEVEL = 60;
	public final static int MAX_BESTIA_SLOTS = 6;
	
	private final Account account;

	/**
	 * Ctor.
	 * 
	 * @param account
	 * @param messageQueue
	 */
	public AccountService(Account account, MessageSender sender) {
		super(sender);
		if (account == null) {
			throw new IllegalArgumentException("Account can not be null.");
		}
		this.account = account;
	}

	public long getAccountId() {
		return account.getId();
	}

	/**
	 * Checks if the password matches the given string.
	 * 
	 * @param password
	 * @return
	 */
	public boolean matchPassword(String password) {
		return account.getPassword().matches(password);
	}

	/**
	 * Sets a new password.
	 * 
	 * @param password
	 */
	public void setPassword(String password) {
		account.setPassword(new Password(password));
	}

	/**
	 * Adds the given amount of gold and silver to the account.
	 * 
	 * @param gold
	 * @param silver
	 */
	public void addGold(int gold, int silver) {
		log.info(String.format(
				"account(id: {0}) received: {1} gold, {2} silver.",
				account.getId(), gold, silver));
		account.setGold(account.getGold() + gold * 100 + silver);
		onChange();
	}

	/**
	 * Tries to remove the given amount of gold from the account. If this does
	 * not succeed false is returned and the gold amount is NOT dropped. If the
	 * account has enough gold and the amount can be removed then it will return
	 * true.
	 * 
	 * @return
	 */
	public boolean dropGold(int gold, int silver) {
		int amount = gold * 100 + silver;
		if (account.getGold() < amount) {
			return false;
		}
		account.setGold(account.getGold() - amount);
		onChange();
		log.info(String.format(
				"account(id: {0}) dropped: {1} gold, {2} silver.",
				account.getId(), gold, silver));
		return true;
	}

	/**
	 * Calculates the current number of bestia slots. This depends on the level
	 * of the bestia master and the number of purchased bestia slots.
	 * 
	 * @return Number of available bestia slots.
	 */
	public int getBestiaSlotNumber() {
		/*final int level = account.getMaster().getLevel();

		if (level >= 90) {
			return 4 + account.getAdditionalBestiaSlots();
		} else if (level >= 30) {
			return 3 + account.getAdditionalBestiaSlots();
		} else if (level >= 15) {
			return 2 + account.getAdditionalBestiaSlots();
		} else {
			return 1 + account.getAdditionalBestiaSlots();
		}*/
		
		return 0;
	}

	@Override
	protected Message getDataChangedMessage() {
		Message msg = null; //new AccountInfoMessage(account);
		log.trace("AccountService detected change: {0}", msg.toString());
		return msg;
	}
	
	public Account getAccount() {
		return account;
	}
}
