package net.bestia.model.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import net.bestia.model.domain.ZoneEntity;

/**
 * DAO for the {@link ZoneEntity}s.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@org.springframework.stereotype.Repository("mapEntitiesDao")
public interface ZoneEntityDao extends CrudRepository<ZoneEntity, Integer> {

	/**
	 * Finds all entities for the given zone.
	 * 
	 * @return The list with all serialized entities.
	 */
	public List<ZoneEntity> findAllByZoneName(String zoneName);

	/**
	 * Deletes all the entities from this zone.
	 * 
	 * @param zoneName
	 */
	public void deleteAllByZoneName(String zoneName);
	
}
