package net.bestia.model.service;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.ItemDAO;
import net.bestia.model.dao.PlayerItemDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.PlayerItem;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * This service kind of manages the user relationship with the inventory. Since
 * access to the inventory might happen on different server (user can have
 * bestias on multiple servers at the same time) no items can be cached. They
 * must be returned from the server all the time.
 * <p>
 * With the help of this class this is archived. When inventory changes notice
 * messages will be generated which can later be retrieved by the server.
 * </p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
@Service("InventoryService")
public class InventoryService {

	private final static Logger log = LogManager.getLogger(InventoryService.class);

	private PlayerItemDAO playerItemDao;
	private AccountDAO accountDao;
	private ItemDAO itemDao;

	@Autowired
	public void setPlayerItemDao(PlayerItemDAO playerItemDao) {
		this.playerItemDao = playerItemDao;
	}

	@Autowired
	public void setAccountDao(AccountDAO accountDao) {
		this.accountDao = accountDao;
	}

	@Autowired
	public void setItemDao(ItemDAO itemDao) {
		this.itemDao = itemDao;
	}

	/**
	 * Checks if an account owns a certain item.
	 * 
	 * @param accId
	 *            Account ID.
	 * @param itemId
	 *            ID of the item to check.
	 * @param amount
	 *            Amount of the item the player should own.
	 * @return TRUE if the item with the given amount is in the inventory. FALSE
	 *         otherwise.
	 */
	public boolean hasItem(long accId, int itemId, int amount) {

		PlayerItem item = playerItemDao.findPlayerItem(accId, itemId);

		if (item == null) {
			return false;
		}

		if (item.getAmount() < amount) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Checks if an account owns a certain item.
	 * 
	 * @param accId
	 *            Account ID.
	 * @param itemDbName
	 *            Name of the item to check.
	 * @param amount
	 *            Amount of the item the player should own.
	 * @return TRUE if the item with the given amount is in the inventory. FALSE
	 *         otherwise.
	 */
	public boolean hasItem(long accId, String itemDbName, int amount) {
		final Item item = itemDao.findItemByName(itemDbName);

		if (item == null) {
			return false;
		}

		return hasItem(accId, item.getId(), amount);
	}

	/**
	 * Adds an item to the account.
	 * 
	 * @param itemId
	 * @param amount
	 * @return
	 */
	public boolean addItem(long accId, int itemId, int amount) {

		// Look if the account already has such an item.
		PlayerItem pitem = playerItemDao.findPlayerItem(accId, itemId);

		if (pitem == null) {
			// New item.

			final Account acc = accountDao.find(accId);
			final Item item = itemDao.findOne(itemId);

			if (acc == null) {
				log.info("Could not find account {}", accId);
				return false;
			}

			if (item == null) {
				log.info("Could not find item {}", itemId);
				return false;
			}

			pitem = new PlayerItem(item, acc, amount);
			playerItemDao.save(pitem);
		} else {
			// Update existing item.
			pitem.setAmount(pitem.getAmount() + amount);
			playerItemDao.update(pitem);
		}

		log.info("Account {} received item {}, amount: {}", accId, itemId, amount);

		return true;
	}

	/**
	 * Adds an item to the account. Like addItem.
	 * 
	 * @param accId
	 * @param itemDbName
	 * @param amount
	 */
	public boolean addItem(long accId, String itemDbName, int amount) {
		final Item item = itemDao.findItemByName(itemDbName);

		if (item == null) {
			return false;
		}

		return addItem(accId, item.getId(), amount);
	}

	/**
	 * Removes an item from the account of this user. The amount must be
	 * positive.
	 * 
	 * @param accId
	 *            Account id.
	 * @param itemId
	 *            Item id.
	 * @param amount
	 *            The amount to be removed. Must be positive.
	 * @return TRUE if the item amount was on this account and the item(s) where
	 *         removed. FALSE if no or not enough items where in this account.
	 */
	public boolean removeItem(long accId, int itemId, int amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Amount must be positive.");
		}

		final PlayerItem item = playerItemDao.findPlayerItem(accId, itemId);
		if (item == null) {
			return false;
		}

		if (item.getAmount() < amount) {
			return false;
		}

		log.info("Account {} removed item {}, amount: {}", accId, itemId, amount);

		if (item.getAmount() > amount) {
			item.setAmount(item.getAmount() - amount);
			playerItemDao.update(item);
		} else {
			// can only be equal amounts.
			playerItemDao.delete(item);
		}

		return true;
	}

	/**
	 * Like {@link removeItem()} but accepting the item database name instead of
	 * the item id.
	 * 
	 * @param accId
	 *            Account id.
	 * @param itemDbName
	 *            Item Database name.
	 * @param amount
	 *            Amount to be removed.
	 * @return TRUE of the item (and the amount) could be removed. FALSE
	 *         otherwise.
	 */
	public boolean removeItem(long accId, String itemDbName, int amount) {
		final Item item = itemDao.findItemByName(itemDbName);

		if (item == null) {
			return false;
		}

		return removeItem(accId, item.getId(), amount);
	}

	/**
	 * Returns a {@link PlayerItem} if the accounts owns this item with the
	 * given player item id.
	 * 
	 * @param playerItemId
	 *            The player item id.
	 * @return The {@link PlayerItem} or NULL if the ID does not exist.
	 */
	public PlayerItem getPlayerItem(int playerItemId) {
		return playerItemDao.find(playerItemId);
	}

	/**
	 * Returns a {@link PlayerItem} with the given account id and item id. If
	 * the item does not exist in the inventory for this given account, null is
	 * returned.
	 * 
	 * @param accId
	 *            Account ID
	 * @param itemId
	 *            Item ID
	 * @return The {@link PlayerItem} or NULL if the item does not exist.
	 */
	public PlayerItem getPlayerItem(long accId, int itemId) {
		return playerItemDao.findPlayerItem(accId, itemId);
	}

	/**
	 * Returns a {@link Item} with the given item db name or NULL if no item
	 * with this name was found.
	 * 
	 * @param itemDbName
	 *            The item database name.
	 * @return The {@link Item} or NULL.
	 */
	public Item getItem(String itemDbName) {
		return itemDao.findItemByName(itemDbName);
	}

	/**
	 * Returns the current item weight of the inventory for this account.
	 * 
	 * @param accId
	 */
	public int getTotalItemWeight(long accId) {
		return playerItemDao.getTotalItemWeight(accId);
	}

	/**
	 * The same as {@link #addItem(long, int, int)} but the item weight will be
	 * considered before adding the item to the inventory. If the new item would
	 * exceed the maxWeight of the inventory then FALSE will be returned.
	 * 
	 * @param accId
	 * @param itemId
	 * @param amount
	 * @param maxWeight
	 * @return
	 */
	public boolean addItem(long accId, int itemId, int amount, int maxWeight) {
		final Item item = itemDao.findOne(itemId);

		if (item == null) {
			throw new IllegalArgumentException("Item with this ID was not found in the database.");
		}

		final int additionalWeight = item.getWeight() * amount;

		if (getTotalItemWeight(accId) + additionalWeight > maxWeight) {
			return false;
		}

		return addItem(accId, itemId, amount);
	}

	/**
	 * Delegates down to the DAO to find all items for the current account.
	 * Note: This will and must change when we switch to bestia based
	 * inventories.
	 * 
	 * @param accId
	 * @return
	 */
	public List<PlayerItem> findPlayerItemsForAccount(long accId) {
		return playerItemDao.findPlayerItemsForAccount(accId);
	}
}
