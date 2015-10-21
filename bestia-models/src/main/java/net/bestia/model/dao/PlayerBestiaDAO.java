package net.bestia.model.dao;

import java.util.Set;

import net.bestia.model.domain.PlayerBestia;

public interface PlayerBestiaDAO extends GenericDAO<PlayerBestia, Integer> {

	/**
	 * Finds all {@link PlayerBestia}s for a given account ID.
	 * 
	 * @param accId
	 *            Account ID to get all bestias.
	 * @return A set of all found {@link PlayerBestia}s for this account.
	 */
	public Set<PlayerBestia> findPlayerBestiasForAccount(long accId);
}
