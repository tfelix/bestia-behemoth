package net.bestia.entity.component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
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
 * @author Thomas Felix
 *
 */

@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
@Service
public class InventoryService {

	private final static Logger LOG = LoggerFactory.getLogger(InventoryService.class);
	private static final int BASE_WEIGHT = 150;

	private final EntityService entityService;
	private final PlayerItemDAO playerItemDao;
	private final AccountDAO accountDao;
	private final ItemDAO itemDao;

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
	 * If the entity also has a {@link StatusComponent} attached it will update
	 * the max weight setting depending on this status strength. The formula is:
	 * <p>
	 * Weight_max = 200 + STR * 4 + 3 * Lv
	 * </p>
	 * If the entity does not have a status component then the weight will be 0.
	 * As a guideline one weight unit loosly resembles 0.1 kg. The entity needs
	 * a level and a status component.
	 *
	 * @param entity
	 *            This entity inventory gets updated with the maximum weight.
	 */
	public void updateMaxWeight(Entity entity) {
		final InventoryComponent invComp = checkAndGetInventoryComp(entity);

		// Now we must check if we have a status component.
		final Optional<StatusComponent> statusComp = entityService.getComponent(entity, StatusComponent.class);

		if (!statusComp.isPresent()) {
			invComp.setMaxWeight(0);
			entityService.updateComponent(invComp);
			return;
		}

		// Currently we can not distinguish between bestia classes.
		final int level = entityService
				.getComponent(entity, LevelComponent.class)
				.map(LevelComponent::getLevel)
				.orElse(1);

		final int str = statusComp.map(c -> c.getStatusPoints().getStrength()).orElse(0);
		final int maxWeight = BASE_WEIGHT + str * 4 * level;
		
		LOG.trace("Setting max weight for entity {} to {}.", entity, maxWeight);

		invComp.setMaxWeight(maxWeight);
		entityService.updateComponent(invComp);
	}

	/**
	 * The maximum number of items are either limited by weight of by a concrete
	 * number of max items. If this number is reached no more items can be added
	 * to the inventory. If this is set to another value then
	 * {@link InventoryComponent#UNLIMITED_ITEMS} then the weight is ignored.
	 *
	 * @param entity
	 *            The entity to update the max num item component.
	 * @param maxNumItems
	 *            The maximum number of items which can be attached to this
	 *            inventory component.
	 */
	public void setMaxItemCount(Entity entity, int maxNumItems) {
		final InventoryComponent invComp = checkAndGetInventoryComp(entity);
		invComp.setMaxItemCount(maxNumItems);
		entityService.updateComponent(invComp);
	}

	/**
	 * Checks if the entity has the inventory component, if not the component
	 * will be added.
	 */
	private InventoryComponent checkAndGetInventoryComp(Entity entity) {
		final Optional<InventoryComponent> invCompOpt = entityService.getComponent(entity, InventoryComponent.class);
		InventoryComponent invComp;
		if (!invCompOpt.isPresent()) {
			invComp = entityService.newComponent(InventoryComponent.class);
			entityService.attachComponent(entity, invComp);
		} else {
			invComp = invCompOpt.get();
		}

		return invComp;
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
		return item != null && item.getAmount() >= amount;
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
		return item != null && hasItem(accId, item.getId(), amount);
	}

	/**
	 * Adds an item to the account.
	 *
	 */
	public boolean addItem(long accId, int itemId, int amount) {

		// Look if the account already has such an item.
		PlayerItem pitem = playerItemDao.findPlayerItem(accId, itemId);

		if (pitem == null) {
			// New item.

			final Account acc = accountDao.findOne(accId);
			final Item item = itemDao.findOne(itemId);

			if (acc == null) {
				LOG.info("Could not find account {}", accId);
				return false;
			}

			if (item == null) {
				LOG.info("Could not find item {}", itemId);
				return false;
			}

			pitem = new PlayerItem(item, acc, amount);
			playerItemDao.save(pitem);
		} else {
			// Update existing item.
			pitem.setAmount(pitem.getAmount() + amount);
			playerItemDao.save(pitem);
		}

		LOG.info("Account {} received item {}, amount: {}", accId, itemId, amount);

		return true;
	}

	/**
	 * Adds an item to the account. Like addItem.
	 *
	 */
	public boolean addItem(long accId, String itemDbName, int amount) {
		final Item item = itemDao.findItemByName(itemDbName);

		return item != null && addItem(accId, item.getId(), amount);

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

		LOG.info("Account {} removed item {}, amount: {}", accId, itemId, amount);

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

		return item != null && removeItem(accId, item.getId(), amount);

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
	 * Delegates down to the DAO to find all items for the current account.
	 * Note: This will and must change when we switch to bestia based
	 * inventories.
	 */
	public List<PlayerItem> findPlayerItemsForAccount(long accId) {
		return playerItemDao.findPlayerItemsForAccount(accId);
	}

	/**
	 * Checks if the user has the given player item with the wanted amount.
	 */
	public boolean hasPlayerItem(int playerItemId, int amount) {
		final PlayerItem item = playerItemDao.findOne(playerItemId);

		return item != null && item.getAmount() >= amount;

	}
}
