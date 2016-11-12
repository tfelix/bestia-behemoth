package net.bestia.model.dao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.Tileset;

/**
 * DAO to query the tileset model.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Repository("tilesetDao")
public interface TilesetDAO extends CrudRepository<Tileset, Long> {

	/**
	 * Returns the tilset which contains the tile with the given GID.
	 * 
	 * @param gid
	 *            The GID to look for.
	 * @return The {@link Tileset} which contains this GID, or null if no
	 *         {@link Tileset} was found.
	 */
	@Query("SELECT t FROM Tileset t WHERE t.minGid <= :gid AND t.maxGid >= :gid")
	public Tileset findByGid(@Param("gid") long gid);
}
