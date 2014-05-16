package net.bestia.core.game.service;

import java.util.concurrent.BlockingQueue;

import net.bestia.core.game.model.Account;
import net.bestia.core.game.model.Password;
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
	
	/**
	 * Returns the account.
	 * @return
	 */
	public Account getAccount() {
		return account;
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
	 * 
	 * 
	 * @param exp
	 */
	/*
	public void addExperience(int exp) {
		if(exp < 0) {
			return;
		}
		account.setExperience(account.getExperience() + exp);
		log.info(String.format("account(id:{0} received: {1} exp.", account.getId(), exp));
		checkLevelUp();
	}

	/**
	 * Checks if the account has enough exp to level up. If this happens perform the
	 * necessairy steps to level up.
	 */
	/*
	private void checkLevelUp() {
		if(account.getMaster().getLevel() >= account.MAX_MASTER_LEVEL) {
			return;
		}
		
		if(account.getExperience() < getLevelUpExperience()) {
			return;
		}
		
		account.setExperience(account.getExperience() - getLevelUpExperience());
		account.setLevel(account.getLevel() + 1);
		// TODO Log.
		// Check recursive if another level up has occured.
		checkLevelUp();
		
	}
	
	/**
	 * Returns the experience points which are needed for the next levelup.
	 * @return
	 */
	/*
	public long getLevelUpExperience() {
		return (long)Math.exp(account.getMaster().getLevel() / 3.0 + 50);
	}
	
	/**
	 * Calculates the current number of bestia slots. This depends on the level
	 * of the bestia master.
	 * @return
	 */
	
	public int getBestiaSlotNumber() {/*
		int slots = account.getMaster().getLevel() / 30 * 3;
		if(slots > AccountData.MAX_BESTIA_SLOTS) {
			return AccountData.MAX_BESTIA_SLOTS;
		}
		return slots;*/
		return 0;
	}

	@Override
	protected Message getDataChangedMessage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void save() {
		// TODO Auto-generated method stub
		
	}
}
