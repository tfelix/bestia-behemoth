package bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import bestia.model.domain.MapParameter;

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
	MapParameter findFirstByOrderByIdDesc();
}
