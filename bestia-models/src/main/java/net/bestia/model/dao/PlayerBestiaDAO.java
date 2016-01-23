package net.bestia.model.dao;

import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import net.bestia.model.domain.PlayerBestia;

public interface PlayerBestiaDAO extends CrudRepository<PlayerBestia, Integer> {

	/**
	 * Finds all {@link PlayerBestia}s for a given account ID.
	 * 
	 * @param accId
	 *            Account ID to get all bestias.
	 * @return A set of all found {@link PlayerBestia}s for this account.
	 */
	@Query("from PlayerBestia pb where pb.owner.id = :owner and pb.id != (select acc.master.id from Account acc where acc.id = :owner)")
	public Set<PlayerBestia> findPlayerBestiasForAccount(@Param("owner") long accId);
}
