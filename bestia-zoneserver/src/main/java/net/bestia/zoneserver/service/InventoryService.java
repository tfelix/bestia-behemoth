package net.bestia.zoneserver.service;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.StatusComponent;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.ItemDAO;
import net.bestia.model.dao.PlayerItemDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.PlayerItem;

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
@Service
public class InventoryService {

	private final static Logger log = LoggerFactory.getLogger(InventoryService.class);

	private static final int BASE_WEIGHT = 150;

	private final PlayerItemDAO playerItemDao;
	private final AccountDAO accountDao;
	private final ItemDAO itemDao;
	private final EntityService entityService;

	@Autowired
	public InventoryService(PlayerItemDAO playerItemDao,
			AccountDAO accDao,
			ItemDAO itemDao,
			EntityService entityService) {

		this.playerItemDao = Objects.requireNonNull(playerItemDao);
		this.accountDao = Objects.requireNonNull(accDao);
		this.itemDao = Objects.requireNonNull(itemDao);
		this.entityService = Objects.requireNonNull(entityService);

	}

	/**
	 * Calculates the max carryable weight of a entity. The formula is:
	 * <p>
	 * Weight_max = 200 + STR * 4 + 3 * Lv
	 * </p>
	 * If the entity does not have a status component then the weight will be 0.
	 * As a guideline one weight unit loosly resembles 0.1 kg. The entity needs
	 * a level and a status component.
	 * 
	 * @param entity
	 *            The entity to get the carry weight for.
	 * @return The weight the entity is able to carry.
	 */
	public int getMaxWeight(Entity entity) {
		// Currently we can not distinguish between bestia classes.
		final int level = entityService
				.getComponent(entity, LevelComponent.class)
				.map(LevelComponent::getLevel)
				.orElse(0);

		if (level == 0) {
			return 0;
		}

		final int str = entityService.getComponent(entity, StatusComponent.class)
				.map(c -> c.getStatusPoints().getStrength())
				.orElse(0);

		return BASE_WEIGHT + str * 4 * level;
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

		final PlayerItem item = playerItemDao.findPlayerItem(accId, itemId);

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

			final Account acc = accountDao.findOne(accId);
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
			playerItemDao.save(pitem);
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
			playerItemDao.save(item);
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
		return playerItemDao.findOne(playerItemId);
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
		if (playerItemDao.countPlayerItemsForAccount(accId) == 0) {
			return 0;
		}

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

	/**
	 * Checks if the user has the given player item with the wanted amount.
	 * 
	 * @param playerItemId
	 * @param amount
	 * @return
	 */
	public boolean hasPlayerItem(int playerItemId, int amount) {
		final PlayerItem item = playerItemDao.findOne(playerItemId);

		if (item == null) {
			return false;
		}

		return item.getAmount() >= amount;
	}
}
