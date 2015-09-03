package net.bestia.model.dao;

import net.bestia.model.domain.Item;

public interface ItemDAO extends GenericDAO<Item, Integer> {

	/**
	 * Returns an item by its item database name. The name is unique. Returns null if the item was not found.
	 * 
	 * @param itemDbName
	 *            Unique database name of an item.
	 * @return The found item. Or null if the item was not found.
	 */
	public Item findItemByName(String itemDbName);
}
