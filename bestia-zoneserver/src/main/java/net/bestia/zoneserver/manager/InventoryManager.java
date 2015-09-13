package net.bestia.zoneserver.manager;

import net.bestia.model.ServiceLocator;
import net.bestia.model.dao.ItemDAO;
import net.bestia.model.dao.PlayerItemDAO;
import net.bestia.model.domain.Item;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.zone.Zone;

/**
 * Wrapper around the {@link InventoryService}. It provides more or less the same methods but it generates translated
 * messages to be send to the user if something with his item changes. It also generates inventory update messages to be
 * send to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryManager {

	private InventoryService inventoryService;
	private ItemDAO itemDao;
	private PlayerItemDAO playerItemDao;
	private long accId;

	public InventoryManager(long accId, ServiceLocator service) {
		if (service == null) {
			throw new IllegalArgumentException("Service can not be null.");
		}

		this.inventoryService = service.getBean(InventoryService.class);
		this.itemDao = service.getBean(ItemDAO.class);
		this.playerItemDao = service.getBean(PlayerItemDAO.class);
		this.accId = accId;
	}

	/**
	 * Adds an item to the users (accounts) inventory. If this is possible (not exeeding account inventory limit) true
	 * is returned. Otherwise false.
	 * 
	 * @param itemId
	 * @return TRUE if the item could be added to the inventory. FALSE if not.
	 */
	public boolean addItem(int itemId, int amount) {

		// TODO Check if the item can be added.

		// TODO Send a message.

		inventoryService.addItem(amount, itemId, amount);

		return true;
	}

	public boolean addItem(String itemDbName, int amount) {

		// TODO Check if the item can be added.

		// TODO Send a message.

		inventoryService.addItem(amount, itemDbName, amount);

		return true;
	}

	/**
	 * Returns the item with the given ID from the database. Or null if the item was not found.
	 * 
	 * @param itemId
	 *            Item ID.
	 * @return The item or NULL of the item could not be found.
	 */
	public Item getItem(int itemId) {
		return itemDao.find(itemId);
	}

	public boolean hasItem(int itemId, int amount) {
		return inventoryService.hasItem(accId, itemId, amount);
	}

	public boolean hasItem(String itemDbName, int amount) {
		return inventoryService.hasItem(accId, itemDbName, amount);
	}

	public boolean removeItem(int itemId, int amount) {

		// TODO if success send inventory update message.

		return inventoryService.removeItem(accId, itemId, amount);
	}

	public boolean removeItem(String itemDbName, int amount) {

		// TODO if success send inventory update message.

		return inventoryService.removeItem(accId, itemDbName, amount);
	}
}
