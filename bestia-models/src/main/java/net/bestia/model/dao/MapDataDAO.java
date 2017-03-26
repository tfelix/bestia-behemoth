package net.bestia.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.MapData;
import net.bestia.model.geometry.Size;

@Repository("mapDataDao")
public interface MapDataDAO extends CrudRepository<MapData, Long> {

	@Query("SELECT MapData md WHERE (md.x < x + width AND md.y < y + height) OR "
			+ "(md.x + md.width < x AND md.y < y + height) OR "
			+ "(md.x < x + width AND md.y + md.height > y) OR "
			+ "(md.x + md.width > x AND md.y + md.height > y)")
	List<MapData> findAllInRange(long x, long y, long width, long height);
	
	/**
	 * Returns the size of this map.
	 * 
	 * @return The size of the map.
	 */
	@Query("SELECT new net.bestia.model.geometry.Size(MAX(md.x + md.width), MAX(md.y + md.height)) FROM MapData md")
	public Size getMapSize();
}
