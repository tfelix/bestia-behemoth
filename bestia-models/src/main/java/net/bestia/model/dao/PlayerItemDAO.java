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
}
