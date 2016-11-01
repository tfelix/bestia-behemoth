package net.bestia.zoneserver.zone.entity.traits;

import net.bestia.model.domain.Item;

/**
 * Entities implementing this interface can be equipped with items.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Equipable {

	/**
	 * Checks if all prerequisites are fulfilled in order to equip this item to
	 * the entity. If another item already occupied this slot the method wont
	 * return false. Upon equipping the other item will rather get removed from
	 * this equipment slot.
	 * 
	 * @param item
	 *            The item to check if it can be equipped.
	 * @return TRUE if the item can currently be equipped. FALSE otherwise.
	 */
	boolean canEquip(Item item);

	/**
	 * Equips the item and apply any status effects bundled with this item. Only
	 * equipment type items can be equipped.
	 * 
	 * @param item
	 *            The item to equip.
	 */
	void equipItem(Item item);

	/**
	 * Removes the item and removes all status effects associated with it.
	 * 
	 * @param item
	 *            The item to remove.
	 */
	void unquipItem(Item item);

}