package net.bestia.model.dao;

import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.PlayerBestia;

/**
 * DAO for accessing and manipulating the {@link PlayerBestia}s.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Repository("playerBestiaDao")
public interface PlayerBestiaDAO extends CrudRepository<PlayerBestia, Long> {

	/**
	 * Finds all {@link PlayerBestia}s for a given account ID.
	 * 
	 * @param accId
	 *            Account ID to get all bestias.
	 * @return A set of all found {@link PlayerBestia}s for this account.
	 */
	@Query("from PlayerBestia pb where pb.owner.id = :owner and pb.id != (select acc.master.id from Account acc where acc.id = :owner)")
	public Set<PlayerBestia> findPlayerBestiasForAccount(@Param("owner") long accId);

	/**
	 * Returns a master player bestia with the given name or null if no such
	 * bestia could be found.
	 * 
	 * @param name
	 *            The name of the master bestia to look for.
	 * @return The found {@link PlayerBestia} or null.
	 */
	@Query("FROM PlayerBestia pb WHERE pb.master != null AND pb.name = :name")
	public PlayerBestia findMasterBestiaWithName(@Param("name") String name);
}
