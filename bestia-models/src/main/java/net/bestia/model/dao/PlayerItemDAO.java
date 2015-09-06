package net.bestia.model.dao;

import java.util.List;

import net.bestia.model.domain.PlayerItem;

public interface PlayerItemDAO extends GenericDAO<PlayerItem, Integer> {

	/**
	 * Returns all PlayerItems for a particular account id.
	 * 
	 * @param accId
	 *            All items of this account are found.
	 * @return A set of the player items.
	 */
	public List<PlayerItem> findPlayerItemsForAccount(long accId);

	/**
	 * Searches if a given account has a particular item. If found it returns the item null otherwise.
	 * 
	 * @param accId
	 *            Account ID.
	 * @param itemId
	 *            The item id.
	 * @return The found PlayerItem, null otherwise.
	 */
	public PlayerItem findPlayerItem(long accId, int itemId);

	/**
	 * Returns the total weight of all items inside the inventory of the given account.
	 * 
	 * @param accId
	 *            Account ID
	 * @return The summed weight of all items.
	 */
	public int getTotalItemWeight(long accId);
}
