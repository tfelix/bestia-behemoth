package bestia.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import bestia.model.domain.MapData;
import bestia.model.geometry.Size;

@Repository("mapDataDao")
public interface MapDataDAO extends CrudRepository<MapData, Long> {

	@Query("FROM MapData md WHERE ((:x BETWEEN md.x AND md.x + md.width) AND (:y BETWEEN md.y AND md.y + md.height)) OR ((:x + :w BETWEEN md.x AND md.x + md.width) AND (:y BETWEEN md.y AND md.y + md.height)) OR ((:x BETWEEN md.x AND md.x + md.width) AND (:y + :h BETWEEN md.y AND md.y + md.height)) OR ((:x + :w BETWEEN md.x AND md.x + md.width) AND (:y + :h BETWEEN md.y AND md.y + md.height))")
	List<MapData> findAllInRange(@Param("x") long x, @Param("y") long y, @Param("w") long width,
			@Param("h") long height);

	/**
	 * Returns the size of this map.
	 * 
	 * @return The size of the map.
	 */
	@Query("SELECT new net.bestia.model.geometry.Size(MAX(md.x + md.width), MAX(md.y + md.height)) FROM MapData md")
	public Size getMapSize();
}
