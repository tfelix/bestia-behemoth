package net.bestia.model.dao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import net.bestia.model.domain.MapParameter;

/**
 * Simple DAO object for accessing the map parameter data.
 * 
 * @author Thomas Felix
 *
 */
@Repository("mapParameterDao")
public interface MapParameterDAO extends CrudRepository<MapParameter, Integer> {

	/**
	 * Returns the latest {@link MapParameter} from the database.
	 * 
	 * @return The latest {@link MapParameter}.
	 */
	@Query("FROM MapParameter mp ORDER BY mp.id DESC")
	MapParameter findLatest();
}
