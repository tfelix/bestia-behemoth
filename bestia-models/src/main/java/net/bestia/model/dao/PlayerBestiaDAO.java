package net.bestia.model.dao;

import java.util.Set;

import net.bestia.model.domain.PlayerBestia;

public interface PlayerBestiaDAO extends GenericDAO<PlayerBestia, Integer> {

	public Set<PlayerBestia> findPlayerBestiasForAccount(long accId);
}
