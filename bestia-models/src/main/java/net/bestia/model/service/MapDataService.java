package net.bestia.model.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.model.dao.MapDataDAO;
import net.bestia.model.domain.MapData;
import net.bestia.model.map.Map;

/**
 * This service saves and retrieves {@link Map}s from the static bestia data
 * storage.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class MapDataService {

	private static final Logger LOG = LoggerFactory.getLogger(MapDataService.class);

	private final MapDataDAO mapDataDao;

	@Autowired
	public MapDataService(MapDataDAO mapDataDao) {

		this.mapDataDao = Objects.requireNonNull(mapDataDao);
	}

	/**
	 * Retrieves and generates the map. It has the dimensions of the given
	 * coordiantes.
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @return
	 */
	public Map getMap(long x, long y, long width, long height) {
		
		return null;
	}
	
	
}
