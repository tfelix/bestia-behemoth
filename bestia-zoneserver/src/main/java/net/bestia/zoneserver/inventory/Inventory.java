package net.bestia.zoneserver.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.bestia.messages.Message;
import net.bestia.messages.inventory.InventoryListMessage;
import net.bestia.messages.inventory.InventoryUpdateMessage;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.PlayerItem;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.entity.traits.Updateable;

/**
 * Wrapper around the {@link InventoryService}. It provides more or less the
 * same methods but it generates translated messages to be send to the user if
 * something with his item changes. It also generates inventory update messages
 * to be send to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Inventory implements Updateable {

	private final InventoryService inventoryService;
	
	private final long accountId;
	private final PlayerBestiaEntity owner;
	
	private final List<Message> messageBuffer = new ArrayList<>();

	public Inventory(PlayerBestiaEntity owner, InventoryService service) {

		if (service == null) {
			throw new IllegalArgumentException("Service can not be null.");
		}
		
		this.inventoryService = service;
		this.owner = Objects.requireNonNull(owner);
		this.accountId = owner.getAccountId();
		
	}

	/**
	 * Adds an item to the users (accounts) inventory. If this is possible (not
	 * exceeding account inventory limit) TRUE is returned. Otherwise FALSE.
	 * 
	 * @param itemId
	 *            The item id.
	 * @param amount
	 *            Amount of the item to add. Must be positive.
	 * @return TRUE if the item could be added to the inventory. FALSE if not.
	 */
	public boolean addItem(int itemId, int amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("Amount can not be null.");
		}

		final boolean success = inventoryService.addItem(accountId , itemId, amount, getMaxWeight());

		if (success) {
			final PlayerItem playerItem = inventoryService.getPlayerItem(accountId, itemId);
			final InventoryUpdateMessage updateMsg = new InventoryUpdateMessage(accountId);
			updateMsg.updateItem(playerItem.getItem(), amount);
			messageBuffer.add(updateMsg);
		} else {
			// Item weight exceeded. Send message.
			// TODO
		}

		return success;
	}

	/**
	 * The current weight of all items in this inventory.
	 * 
	 * @return Weight of all items.
	 */
	public int getCurrentWeight() {
		return inventoryService.getTotalItemWeight(accountId);
	}

	/**
	 * @see InventoryManager#addItem(int, int).
	 * 
	 * @param itemDbName
	 *            Item database name.
	 * @param amount
	 *            The amount of the item to add. Must be positive.
	 * @return TRUE if the item could be added. FALSE otherwise (inventory
	 *         weight exceeded for example).
	 */
	public boolean addItem(String itemDbName, int amount) {
		final Item item = inventoryService.getItem(itemDbName);
		if (item == null) {
			throw new IllegalArgumentException("Unknown item db name.");
		}
		return addItem(item.getId(), amount);
	}
	
	public boolean hasPlayerItem(int playerItemId, int amount) {
		return inventoryService.hasPlayerItem(playerItemId, amount);
	}

	public boolean hasItem(int itemId, int amount) {
		return inventoryService.hasItem(accountId, itemId, amount);
	}

	public boolean hasItem(String itemDbName, int amount) {
		return inventoryService.hasItem(accountId, itemDbName, amount);
	}

	/**
	 * Removes the given item with the item ID from the inventory of this
	 * player. A {@link InventoryUpdateMessage} is then send to the player.
	 * 
	 * @param itemId
	 *            Item ID of the item to be removed.
	 * @param amount
	 *            The mount of the item to remove. Must be positive.
	 * @return TRUE if the item amount was removed. FALSE if the item could not
	 *         be removed.
	 */
	public boolean removeItem(int itemId, int amount) {
		final PlayerItem playerItem = getPlayerItemById(itemId);
		final boolean success = inventoryService.removeItem(accountId, itemId, amount);

		if (success) {
			final InventoryUpdateMessage updateMsg = new InventoryUpdateMessage(accountId);
			updateMsg.updateItem(playerItem.getItem(), -1 * amount);
			messageBuffer.add(updateMsg);
		}

		return success;
	}

	/**
	 * Creates a {@link InventoryListMessage} from the current inventory
	 * contents.
	 * 
	 * @return
	 */
	public InventoryListMessage getInventoryListMessage() {
		final List<PlayerItem> items = inventoryService.findPlayerItemsForAccount(accountId);

		// Generate a list of inventory items.
		final InventoryListMessage invMsg = new InventoryListMessage();
		invMsg.setPlayerItems(items);
		
		invMsg.setAccountId(accountId);

		return invMsg;
	}

	/**
	 * Returns the {@link PlayerItem} by an item ID. NULL if the player does not
	 * own this item.
	 * 
	 * @param itemId
	 *            Item ID.
	 * @return {@link PlayerItem} or NULL if the player does not own the item.
	 */
	public PlayerItem getPlayerItemById(int itemId) {
		return inventoryService.getPlayerItem(accountId, itemId);
	}

	/**
	 * @see Inventory#removeItem(int, int)
	 * 
	 * @param itemDbName
	 *            Item Database name of the item to be removed.
	 * @param amount
	 *            The amount of the item to be removed. Must be positive.
	 * @return TRUE if the item amount was removed. FALSE if the item could not
	 *         be removed.
	 */
	public boolean removeItem(String itemDbName, int amount) {
		final Item item = inventoryService.getItem(itemDbName);
		return removeItem(item.getId(), amount);
	}

	/**
	 * Calculates the max weight of a bestia. The formula is:
	 * <p>
	 * Weight_max = 150 + ATK * 4 + 3 * Lv
	 * </p>
	 * 
	 * @param bestia
	 * @return
	 */
	public int getMaxWeight() {
		// Currently we can not distinguesh between bestia classes.
		final int baseWeight = 300;
		final int weight = (int)(baseWeight + 200.0 / owner.getLevel() + 5 * owner.getStatusPoints().getAtk());
		return weight;
	}

	public PlayerItem getPlayerItem(int playerItemId) {
		return inventoryService.getPlayerItem(playerItemId);
	}

	@Override
	public List<Message> getUpdates() {
		final List<Message> msgs = new ArrayList<>(messageBuffer);
		messageBuffer.clear();
		return msgs;
	}
}
