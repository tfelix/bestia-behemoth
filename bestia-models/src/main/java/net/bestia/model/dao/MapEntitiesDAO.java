package net.bestia.model.dao;

import java.util.List;

import org.springframework.data.repository.Repository;

import net.bestia.model.domain.MapEntity;

/**
 * DAO for the {@link MapEntity}s.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface MapEntitiesDAO extends Repository<MapEntity, Integer> {

	/**
	 * Finds all entities for the given zone.
	 * 
	 * @return The list with all serialized entities.
	 */
	public List<MapEntity> findAllByZoneName(String zoneName);

	/**
	 * Deletes all the entities from this zone.
	 * 
	 * @param zoneName
	 */
	public void deleteAllByZoneName(String zoneName);
}
