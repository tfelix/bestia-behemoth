package net.bestia.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.Tile;
import net.bestia.model.geometry.Size;

/**
 * DAO to query the tiles.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Repository("tileDao")
public interface TileDAO extends CrudRepository<Tile, Long> {

	/**
	 * Returns the size of this map.
	 * 
	 * @return The size of the map.
	 */
	@Query("SELECT new net.bestia.model.geometry.Size(MAX(t.x), MAX(t.y)) FROM Tile t")
	public Size getMapSize();

	/**
	 * Returns all tile data which is in range.
	 * 
	 * @param x
	 *            Start x
	 * @param y
	 *            Start y
	 * @param width
	 *            Width
	 * @param height
	 *            Height
	 * @return All tiles which lie in this range.
	 */
	@Query("FROM Tile as t WHERE (t.x BETWEEN :x AND (:x + :w)) AND (t.y BETWEEN :y AND (:y + :h))")
	public List<Tile> getTilesInRange(@Param("x") long x,
			@Param("y") long y,
			@Param("w") long width,
			@Param("h") long height);
}
