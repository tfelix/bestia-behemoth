package net.bestia.core.game.service;

import java.util.concurrent.BlockingQueue;

import net.bestia.core.game.model.Account;
import net.bestia.core.game.model.Password;
import net.bestia.core.message.AccountInfoMessage;
import net.bestia.core.message.Message;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Manages the business logic of the AccountData.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class AccountService extends net.bestia.core.game.service.Service {
	
	private static final Logger log = LogManager.getLogger(AccountService.class);
	
	private Account account;
	
	/**
	 * Ctor.
	 * @param account
	 * @param messageQueue
	 */
	public AccountService(Account account, BlockingQueue<Message> messageQueue) {
		super(messageQueue);
		if(account == null) {
			throw new IllegalArgumentException("Account can not be null.");
		}
		this.account = account;
	}
	
	public int getAccountId() {
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
		log.info(String.format("account(id: {0}) received: {1} gold, {2} silver.",
				account.getId(), gold, silver));
		account.setGold(account.getGold() + gold * 100 + silver);
		onChange();
	}
	
	/**
	 * Tries to remove the given amount of gold from the account. If this does not succeed
	 * false is returned and the gold amount is NOT dropped. If the account has enough gold
	 * and the amount can be removed then it will return true.
	 * 
	 * @return
	 */
	public boolean dropGold(int gold, int silver) {
		int amount = gold * 100 + silver;
		if(account.getGold() < amount) {
			return false;
		}
		account.setGold(account.getGold() - amount);
		onChange();
		log.info(String.format("account(id: {0}) dropped: {1} gold, {2} silver.",
				account.getId(), gold, silver));
		return true;
	}
	
	/**
	 * Calculates the current number of bestia slots. This depends on the level
	 * of the bestia master and the number of purchased bestia slots.
	 * 
	 * @return
	 */
	public int getBestiaSlotNumber() {
		int slots = account.getMaster().getLevel() / 30 * 3;
		slots += account.getBestiaSlots() + account.getAdditionalBestiaSlots(); 
		if(slots > Account.MAX_BESTIA_SLOTS) {
			slots = Account.MAX_BESTIA_SLOTS;
		}
		
		return slots;
	}

	@Override
	protected Message getDataChangedMessage() {
		Message msg = new AccountInfoMessage(account);
		log.trace("AccountService detected change: {0}", msg.toString());
		return msg;
	}
}
